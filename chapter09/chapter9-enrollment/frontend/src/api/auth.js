import { http } from './requestJson.js';
const isIdentity = (value) =>
  Boolean(
    value &&
      typeof value.username === 'string' &&
      ['STUDENT', 'TEACHER'].includes(value.role) &&
      Number.isSafeInteger(value.personId) &&
      value.personId > 0,
  );
export function createAuthApi(client = http) {
  return {
    me: () => client.requestJson('/api/me', { validate: isIdentity }),
    async login(username, password) {
      client.setCredentials(username, password);
      try {
        return await client.requestJson('/api/me', { validate: isIdentity });
      } catch (error) {
        client.clearCredentials();
        throw error;
      }
    },
    async logout() {
      client.clearCredentials();
    },
  };
}
export const authApi = createAuthApi();
