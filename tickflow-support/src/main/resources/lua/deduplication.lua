-- KEYS[1]: 去重键 (如 tickflow:dedup:content:xxxx)
-- ARGV[1]: 当前时间戳 (毫秒)
-- ARGV[2]: 时间窗口大小 (毫秒，例如 5 分钟 = 300000)
-- ARGV[3]: 窗口内最大允许发送次数 (例如 1)
-- ARGV[4]: 唯一任务标识 (NanoId / messageId)
-- ARGV[5]: 整个 Key 的过期时间 (秒)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local max_limit = tonumber(ARGV[3])
local member = ARGV[4]
local expire_time = tonumber(ARGV[5])

-- 1. 清理时间窗口之外的过期历史记录
local min_score = now - window
redis.call('ZREMRANGEBYSCORE', key, 0, min_score)

-- 2. 统计当前时间窗口内已有的发送次数
local current_count = redis.call('ZCARD', key)

-- 3. 判断是否超过阈值
if current_count >= max_limit then
    return 1 -- 重复，需要拦截！
end

-- 4. 未超过阈值：记录本次发送时间戳
redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, expire_time)

return 0 -- 未重复，放行！
