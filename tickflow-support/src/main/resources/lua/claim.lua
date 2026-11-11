-- KEYS[1]: 等待中 tickflow:delay
-- KEYS[2]: 处理中 tickflow:delay:processing
-- ARGV[1]: 当前毫秒时间戳  ARGV[2]: 租约到期时间戳  ARGV[3]: 最多取多少条
-- 返回：本次认领到的 messageId 列表

-- 1. 回收：处理中但租约已过期（认领它的实例挂了），续租后重新交给本次投递

local expired = redis.call('ZRANGEBYSCORE', KEYS[2], 0, ARGV[1], 'LIMIT', 0, ARGV[3])
for i, id in ipairs(expired) do
    redis.call('ZADD', KEYS[2], ARGV[2], id)
end

-- 2. 认领：等待中已到期的，从 delay 挪到 processing
local ids = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[3])
for i, id in ipairs(ids) do
    redis.call('ZREM', KEYS[1], id)
    redis.call('ZADD', KEYS[2], ARGV[2], id)
end

-- 3. 合并两批一起返回
for i, id in ipairs(expired) do
    table.insert(ids, id)
end
return ids