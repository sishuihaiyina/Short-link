-- 用户操作流量风控：固定窗口计数
local username = KEYS[1]
local timeWindow = tonumber(ARGV[1]) -- 时间窗口，单位：秒

-- 构造 Redis 中存储用户访问次数的键名
local accessKey = "short-link:user-flow-risk-control:" .. username

-- 原子递增访问次数
local currentAccessCount = redis.call("INCR", accessKey)

-- 只在第一次创建时设置过期时间。
-- 如果每次请求都 EXPIRE，窗口会被不断续期，计数只增不减，相当于用户永远出不来。
if currentAccessCount == 1 then
    redis.call("EXPIRE", accessKey, timeWindow)
end

return currentAccessCount
