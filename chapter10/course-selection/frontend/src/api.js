export class ApiError extends Error {
  constructor(
    message,
    { code = 'NETWORK_ERROR', status = 0, requestId = null, uncertain = false, fields = {} } = {},
  ) {
    super(message);
    this.name = 'ApiError';
    Object.assign(this, { code, status, requestId, uncertain, fields });
  }
}

/** 写请求断开时可能已经提交；由页面查询结果后决定是否沿用原键重试。 */
export function createApiClient({
  fetchImpl = globalThis.fetch.bind(globalThis),
  timeoutMs = 12000,
} = {}) {
  let credentials = null;

  function enrollmentResult(result, teachingClassId, uncertain) {
    if (
      !Number.isSafeInteger(result?.id) ||
      result.id <= 0 ||
      !Number.isSafeInteger(result.teachingClassId) ||
      result.teachingClassId <= 0 ||
      (teachingClassId !== undefined && result.teachingClassId !== teachingClassId) ||
      result.status !== 'ACTIVE' ||
      typeof result.courseName !== 'string' ||
      !result.courseName.trim()
    ) {
      throw new ApiError('选课结果格式异常，请保留原请求确认结果。', {
        code: 'INVALID_RESPONSE',
        status: 200,
        uncertain,
      });
    }
    return result;
  }

  async function request(path, { method = 'GET', body, key } = {}) {
    const write = method !== 'GET';
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const headers = { Accept: 'application/json' };
    if (credentials)
      headers.Authorization = `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (key) headers['Idempotency-Key'] = key;
    try {
      const response = await fetchImpl(`/api${path}`, {
        method,
        headers,
        credentials: 'omit',
        signal: controller.signal,
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      });
      if (response.status === 204) {
        if (method !== 'DELETE')
          throw new ApiError('响应状态与接口约定不符。', {
            code: 'INVALID_RESPONSE',
            status: 204,
            uncertain: write,
          });
        return undefined;
      }
      if (response.ok && response.status !== 200)
        throw new ApiError('响应状态与接口约定不符。', {
          code: 'INVALID_RESPONSE',
          status: response.status,
          uncertain: write,
        });
      // 代理错误可能返回HTML，页面只显示约定的错误信息。
      const payload = await response.json().catch((error) => {
        if (controller.signal.aborted) throw error;
        return null;
      });
      const requestId = response.headers?.get?.('X-Request-Id') ?? null;
      if (
        !response.ok &&
        (!payload ||
          typeof payload.code !== 'string' ||
          typeof payload.message !== 'string' ||
          typeof payload.requestId !== 'string' ||
          !payload.fields ||
          typeof payload.fields !== 'object' ||
          Array.isArray(payload.fields) ||
          (requestId && requestId !== payload.requestId))
      ) {
        throw new ApiError('错误响应格式异常，请保留原请求确认结果。', {
          code: 'INVALID_RESPONSE',
          status: response.status,
          requestId,
          uncertain: write,
        });
      }
      if (!response.ok) {
        throw new ApiError(
          payload?.message ||
            {
              401: '账号或密码错误，请重新登录。',
              403: '当前账号没有操作权限。',
              404: '记录不存在。',
            }[response.status] ||
            '服务暂时不可用，请稍后查询。',
          {
            status: response.status,
            code: payload?.code || `HTTP_${response.status}`,
            requestId: requestId ?? payload?.requestId,
            fields: payload?.fields,
            uncertain: write && response.status >= 500,
          },
        );
      }
      if (payload == null)
        throw new ApiError('响应格式异常，请查询最新记录。', {
          code: 'INVALID_RESPONSE',
          status: response.status,
          uncertain: write,
        });
      return payload;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      const timedOut = controller.signal.aborted;
      throw new ApiError(timedOut ? '请求超时。' : '网络连接中断或服务尚未启动。', {
        code: timedOut ? 'TIMEOUT' : 'NETWORK_ERROR',
        uncertain: write,
      });
    } finally {
      clearTimeout(timer);
    }
  }

  return {
    setCredentials(username, password) {
      credentials = { username, password };
    },
    clearCredentials() {
      credentials = null;
    },
    me: () => request('/me'),
    classes: () => request('/teaching-classes'),
    myEnrollments: () => request('/enrollments/me'),
    requestStatus: async (key) =>
      enrollmentResult(
        await request(`/enrollment-requests/${encodeURIComponent(key)}`),
        undefined,
        false,
      ),
    enroll: async (teachingClassId, key) => {
      const result = await request('/enrollments', {
        method: 'POST',
        body: { teachingClassId },
        key,
      });
      return enrollmentResult(result, teachingClassId, true);
    },
    drop: (id) => request(`/enrollments/${id}`, { method: 'DELETE' }),
    roster: (classId) => request(`/teaching-classes/${classId}/roster`),
    score: (id, score) => request(`/enrollments/${id}/score`, { method: 'PUT', body: { score } }),
  };
}
