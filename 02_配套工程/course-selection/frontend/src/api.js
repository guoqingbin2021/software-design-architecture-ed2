export class ApiError extends Error {
  constructor(message, { code = 'NETWORK_ERROR', status = 0, requestId = null, uncertain = false } = {}) {
    super(message);
    this.name = 'ApiError';
    Object.assign(this, { code, status, requestId, uncertain });
  }
}

/** No automatic retries: a disconnected write can already have committed. */
export function createApiClient({ fetchImpl = globalThis.fetch.bind(globalThis), timeoutMs = 12000 } = {}) {
  let credentials = null;

  async function request(path, { method = 'GET', body, key } = {}) {
    const write = method !== 'GET';
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const headers = { Accept: 'application/json' };
    if (credentials) headers.Authorization = `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (key) headers['Idempotency-Key'] = key;
    try {
      const response = await fetchImpl(`/api${path}`, { method, headers, signal: controller.signal, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
      if (response.status === 204) return undefined;
      // A proxy or failed upstream may return HTML, so never display its raw body.
      const payload = await response.json().catch((error) => {
        if (controller.signal.aborted) throw error;
        return null;
      });
      if (!response.ok) {
        throw new ApiError(payload?.message || ({ 401: '账号或密码错误，请重新登录。', 403: '当前账号没有操作权限。', 404: '记录不存在。' }[response.status] || '服务暂时不可用，请稍后查询。'), {
          status: response.status, code: payload?.code || `HTTP_${response.status}`, requestId: payload?.requestId, uncertain: write && response.status >= 500,
        });
      }
      if (payload == null) throw new ApiError('响应格式异常，请查询最新记录。', { code: 'INVALID_RESPONSE', status: response.status, uncertain: write });
      return payload;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      const timedOut = controller.signal.aborted;
      throw new ApiError(timedOut ? '请求超时。' : '网络连接中断或服务尚未启动。', { code: timedOut ? 'TIMEOUT' : 'NETWORK_ERROR', uncertain: write });
    } finally {
      clearTimeout(timer);
    }
  }

  return {
    setCredentials(username, password) { credentials = { username, password }; },
    clearCredentials() { credentials = null; },
    me: () => request('/me'),
    classes: () => request('/teaching-classes'),
    myEnrollments: () => request('/enrollments/me'),
    enroll: (teachingClassId, key) => request('/enrollments', { method: 'POST', body: { teachingClassId }, key }),
    drop: (id) => request(`/enrollments/${id}`, { method: 'DELETE' }),
    roster: (classId) => request(`/teaching-classes/${classId}/roster`),
    score: (id, score) => request(`/enrollments/${id}/score`, { method: 'PUT', body: { score } }),
  };
}
