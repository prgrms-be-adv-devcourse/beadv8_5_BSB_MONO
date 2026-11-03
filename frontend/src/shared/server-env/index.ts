// 서버 환경변수 검사는 zod(압축 후 약 90KB)를 쓴다. 브라우저 화면이 가져가는 @/shared/config 묶음에 넣으면 zod가 같이 실려서 따로 둔다.
export { getServerEnv, type ServerEnv } from '../config/env'
