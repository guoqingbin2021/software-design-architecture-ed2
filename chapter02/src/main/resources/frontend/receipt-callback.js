/**
 * 代码清单2-28：前端Promise异步请求与链式回调（示例，2.6.2案例2-7）。
 */
function fetchAndRenderReceipt(axios, renderReceipt, showError, hideLoading) {
  return axios
    .get('/api/sale/12345')
    .then((resp) => renderReceipt(resp.data)) // 成功回调：渲染小票数据
    .catch((err) => showError(err.message)) // 失败回调：提示错误原因
    .finally(() => hideLoading()); // 收尾回调：无论成败均关闭加载提示
}

module.exports = { fetchAndRenderReceipt };
