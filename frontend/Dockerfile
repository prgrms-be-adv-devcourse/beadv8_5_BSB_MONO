# AWS(EC2 Docker Compose)용 이미지. Vercel 배포에는 쓰이지 않는다.
#   docker build -t running-web .
#   docker run -p 3000:3000 -e BACKEND_URL=http://bukang-gateway:8000 running-web
#
# 빌드 시점 값(next.config에 고정): API_MOCKING, IMAGE_REMOTE_HOSTS, NEXT_PUBLIC_*
# 빌드·실행 둘 다: SITE_URL (정적 페이지는 빌드 값, 실행 중에 그리는 페이지는 실행 값을 쓴다)
# 실행 시점 값: BACKEND_URL (서버 컴포넌트가 백엔드를 직접 부를 때 쓰는 내부 주소, 게이트웨이)

ARG NODE_VERSION=24

FROM node:${NODE_VERSION}-alpine AS base
RUN corepack enable
WORKDIR /app

FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile --ignore-scripts

FROM base AS builder
ARG API_MOCKING=off
ARG IMAGE_REMOTE_HOSTS=
ARG NEXT_PUBLIC_IMAGE_CDN_URL=
# 정적 페이지의 metadataBase(og:image 절대 주소)가 빌드 때 이 값으로 고정된다
ARG SITE_URL=
# 토스 결제창 클라이언트 키(test_ck_만). 비우면 API_MOCKING=off(이 기본값)에서는 충전 버튼이 실패 화면으로 간다
ARG NEXT_PUBLIC_TOSS_CLIENT_KEY=
# Google 태그 관리자 컨테이너 ID. 비우면 GTM을 넣지 않고 사용자 활동 이벤트도 보내지 않는다
ARG NEXT_PUBLIC_GTM_ID=
ENV API_MOCKING=${API_MOCKING} \
    IMAGE_REMOTE_HOSTS=${IMAGE_REMOTE_HOSTS} \
    NEXT_PUBLIC_IMAGE_CDN_URL=${NEXT_PUBLIC_IMAGE_CDN_URL} \
    SITE_URL=${SITE_URL} \
    NEXT_PUBLIC_TOSS_CLIENT_KEY=${NEXT_PUBLIC_TOSS_CLIENT_KEY} \
    NEXT_PUBLIC_GTM_ID=${NEXT_PUBLIC_GTM_ID} \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM node:${NODE_VERSION}-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN addgroup -S nodejs -g 1001 && adduser -S nextjs -u 1001 -G nodejs
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
