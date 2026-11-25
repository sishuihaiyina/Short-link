import http from '../axios'

export default {
  // 死信分页
  pageDlq(data) {
    return http({
      method: 'get',
      params: data,
      url: 'mq/dlq'
    })
  },
  // 重放一条死信消息
  replayDlq(id) {
    return http({
      method: 'post',
      params: { id },
      url: 'mq/dlq/replay'
    })
  }
}
