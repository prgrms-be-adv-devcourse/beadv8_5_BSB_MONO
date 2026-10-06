export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
    readonly url: string,
  ) {
    super(`API ${status}: ${url}`)
    this.name = 'ApiError'
  }
}
