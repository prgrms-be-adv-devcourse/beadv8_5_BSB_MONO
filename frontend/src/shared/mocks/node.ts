import { setupServer } from 'msw/node'

import { handlers } from './handlers'

/** 테스트(Node)용 MSW 서버. 브라우저 번들에 들어가지 않게 index.ts에서 내보내지 않는다. */
export const server = setupServer(...handlers)
