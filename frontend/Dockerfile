# AWS(EC2 Docker Compose)용 이미지. Vercel 배포에는 쓰이지 않는다.
#   docker build -t running-web .
#   docker run -p 3000:3000 -e BACKEND_URL=http://bukang-app:8080 running-web
#
# 빌드 시점 값(next.config에 고정): API_MOCKING, IMAGE_REMOTE_HOSTS, NEXT_PUBLIC_*
# 실행 시점 값: BACKEND_URL (서버 컴포넌트가 백엔드를 직접 부를 때 쓰는 내부 주소)

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
ENV API_MOCKING=${API_MOCKING} \
    IMAGE_REMOTE_HOSTS=${IMAGE_REMOTE_HOSTS} \
    NEXT_PUBLIC_IMAGE_CDN_URL=${NEXT_PUBLIC_IMAGE_CDN_URL} \
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
