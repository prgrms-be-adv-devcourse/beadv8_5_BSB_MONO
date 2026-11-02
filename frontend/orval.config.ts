import { readFileSync } from 'node:fs'
import { defineConfig, type Options } from 'orval'

/** 서비스 이름 → 로컬 포트. api:pull(scripts/pull-openapi.mjs)도 같은 파일을 읽는다. */
const SERVICES: Record<string, number> = JSON.parse(readFileSync('./openapi/services.json', 'utf8'))

type Service = string

/**
 * 백엔드 서비스별 OpenAPI 명세로 API 코드를 만든다.
 *
 *   pnpm api:generate   # openapi/<서비스>.json → src/shared/api/generated/<서비스>/
 *   pnpm api:pull       # 로컬에서 띄운 서비스의 /v3/api-docs로 openapi/<서비스>.json을 새로 받는다
 *
 * 명세는 openapi/ 에 파일로 둔다. 백엔드를 띄우지 않아도 생성할 수 있고, 명세가 바뀐 것을 PR diff로 볼 수 있다.
 *
 * 결과 (서비스마다 한 폴더)
 * - <서비스>.ts         : 요청 함수 + TanStack Query 훅 (customFetch를 지나며 RsData의 data만 돌려준다)
 * - <서비스>.schemas.ts : 요청·응답 타입
 * - <서비스>.msw.ts     : MSW 핸들러 (src/shared/mocks/handlers.ts에 등록). 명세의 응답 예시를 그대로 돌려준다
 * - <서비스>.zod.ts     : 폼 검증에 쓸 zod 스키마
 */

interface Operation {
  operationId?: string
  parameters?: { in?: string; name?: string }[]
  responses?: Record<string, { content?: Record<string, { schema?: unknown }> }>
}

interface Spec {
  paths?: Record<string, Partial<Record<string, Operation>>>
  components?: { schemas?: Record<string, unknown> }
}

/**
 * 성공 응답에 스키마가 없는 API를 보충한다 (openapi/supplements/<서비스>.json).
 *
 * 백엔드가 성공 응답을 @ApiResponse(content = @Content(examples = ...))로만 적으면 springdoc이
 * 반환 타입(RsData<XxxDto>) 스키마를 넣지 않는다. 백엔드에서 useReturnTypeSchema = true를 붙이면
 * springdoc이 같은 이름(RsDataXxxDto)의 스키마를 만들므로, 그때 보충 파일에서 그 API를 지운다.
 */
interface Supplement {
  responses: Record<string, Record<string, { wrapper: string; data: unknown }>>
  schemas: Record<string, unknown>
  /**
   * 백엔드 명세에 아직 없는 API. 프론트 목업으로만 쓰는 API(예: 휴대폰 인증)를 적는다.
   * 백엔드 명세에 같은 경로가 생기면 백엔드 것을 쓰고 이 항목은 무시한다.
   */
  paths?: Record<string, Partial<Record<string, Operation>>>
  /** 백엔드 스키마에 칸을 더한다 (스키마 이름 → 더할 properties). 백엔드가 그 칸을 내면 지운다. */
  extraProperties?: Record<string, Record<string, unknown>>
}

function readSupplement(service: Service): Supplement | undefined {
  try {
    return JSON.parse(readFileSync(`./openapi/supplements/${service}.json`, 'utf8')) as Supplement
  } catch {
    return undefined
  }
}

/** 게이트웨이가 토큰을 보고 넣어 주는 헤더. 프론트가 보내지 않으므로 요청 함수 인자에서 뺀다. */
const GATEWAY_HEADERS = new Set(['x-member-id'])

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete']

function transform(service: Service, spec: Spec): Spec {
  const supplement = readSupplement(service)
  const schemas = { ...spec.components?.schemas, ...supplement?.schemas }
  spec.components = { ...spec.components, schemas }
  spec.paths = { ...supplement?.paths, ...spec.paths }
  for (const [name, properties] of Object.entries(supplement?.extraProperties ?? {})) {
    const schema = schemas[name] as { properties?: Record<string, unknown> } | undefined
    if (schema) schemas[name] = { ...schema, properties: { ...properties, ...schema.properties } }
  }

  for (const pathItem of Object.values(spec.paths ?? {})) {
    for (const method of HTTP_METHODS) {
      const operation = pathItem[method]
      if (!operation) continue

      operation.parameters = operation.parameters?.filter(
        (p) => !(p.in === 'header' && GATEWAY_HEADERS.has(p.name?.toLowerCase() ?? '')),
      )

      const patches = supplement?.responses[operation.operationId ?? ''] ?? {}
      for (const [code, { wrapper, data }] of Object.entries(patches)) {
        schemas[wrapper] = {
          type: 'object',
          properties: {
            status: { type: 'integer', format: 'int32' },
            message: { type: 'string' },
            data,
          },
        }
        const content = operation.responses?.[code]?.content?.['application/json']
        if (content) content.schema = { $ref: `#/components/schemas/${wrapper}` }
      }
    }
  }
  return spec
}

function serviceConfig(service: Service): Options {
  return {
    input: {
      target: `./openapi/${service}.json`,
      override: {
        transformer: (spec) => transform(service, spec as Spec) as typeof spec,
      },
    },
    output: {
      mode: 'split',
      target: `src/shared/api/generated/${service}/${service}.ts`,
      client: 'react-query',
      httpClient: 'fetch',
      mock: { generators: [{ type: 'msw', useExamples: true, delay: false }] },
      override: {
        mutator: { path: 'src/shared/api/fetcher.ts', name: 'customFetch' },
        fetch: { includeHttpResponseReturnType: false },
      },
    },
  }
}

function zodConfig(service: Service): Options {
  return {
    input: {
      target: `./openapi/${service}.json`,
      override: {
        transformer: (spec) => transform(service, spec as Spec) as typeof spec,
      },
    },
    output: {
      mode: 'single',
      target: `src/shared/api/generated/${service}/${service}.zod.ts`,
      client: 'zod',
    },
  }
}

const services = Object.keys(SERVICES)

export default defineConfig(
  Object.fromEntries(
    services.flatMap((service) => [
      [service, serviceConfig(service)],
      [`${service}Zod`, zodConfig(service)],
    ]),
  ),
)
