export class ApiError extends Error {
  constructor(message, details = {}) {
    super(message);
    this.name = 'ApiError';
    Object.assign(this, details);
  }
}

/** requestId comes from the server response; it is never the business idempotency key. */
export function createHttpClient({
  fetchImpl = (...args) => fetch(...args),
  timeoutMs = 10000,
} = {}) {
  let credentials = null;
  function setCredentials(username, password) {
    credentials = { username, password };
  }
  function clearCredentials() {
    credentials = null;
  }

  async function requestJson(path, options = {}) {
    const { expectedStatus = 200, validate, timeout = timeoutMs, ...init } = options;
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (credentials)
      headers.set(
        'Authorization',
        `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`,
      );
    const controller = new AbortController();
    const abort = () => controller.abort(init.signal?.reason);
    if (init.signal?.aborted) abort();
    else init.signal?.addEventListener('abort', abort, { once: true });
    const timer = setTimeout(() => controller.abort(), timeout);
    let response;
    let requestId = null;
    const failure = (message, kind) =>
      new ApiError(message, {
        kind,
        status: response?.status ?? 0,
        requestId,
      });
    try {
      response = await fetchImpl(path, {
        ...init,
        method,
        headers,
        credentials: 'omit',
        signal: controller.signal,
      });
      const responseRequestId = response.headers.get('X-Request-Id');
      requestId = responseRequestId;
      if (response.status === 204) {
        if (expectedStatus !== 204) throw failure('响应状态与接口约定不符', 'protocol');
        return null; // A 204 response has no JSON body.
      }
      let body;
      try {
        const text = await response.text();
        body = text ? JSON.parse(text) : null;
      } catch (cause) {
        if (controller.signal.aborted) throw failure('请求超时或等待已取消', 'network');
        throw failure('服务器响应格式与接口约定不符', 'protocol');
      }
      if (!response.ok) {
        if (
          !body ||
          typeof body.code !== 'string' ||
          !body.code ||
          typeof body.message !== 'string' ||
          typeof body.requestId !== 'string' ||
          !body.requestId ||
          !body.fields ||
          typeof body.fields !== 'object' ||
          Array.isArray(body.fields) ||
          (responseRequestId && responseRequestId !== body.requestId)
        ) {
          throw failure('错误响应与接口约定不符', 'protocol');
        }
        requestId = responseRequestId ?? body.requestId;
        throw Object.assign(failure(body.message, 'http'), {
          code: body.code,
          fields: body.fields,
        });
      }
      if (response.status !== expectedStatus) throw failure('响应状态与接口约定不符', 'protocol');
      if (validate && !validate(body)) throw failure('成功响应的数据与接口约定不符', 'protocol');
      return body;
    } catch (cause) {
      if (cause instanceof ApiError) throw cause;
      throw failure(
        controller.signal.aborted ? '请求超时或等待已取消' : '无法取得服务器响应',
        'network',
      );
    } finally {
      clearTimeout(timer);
      init.signal?.removeEventListener('abort', abort);
    }
  }
  return { requestJson, setCredentials, clearCredentials };
}

export const http = createHttpClient();
export const requestJson = http.requestJson;
