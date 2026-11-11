-- KEYS[1]: 频次计数键，如 tickflow:dedup:frequency:40:a@qq.com
-- ARGV[1]: 过期秒数（距离今天午夜的剩余秒数）
-- 返回：自增后的计数

local key = KEYS[1]
local expire = ARGV[1]

local count =redis.call('INCR',key)
if count == 1 then
    redis.call('EXPIRE',key,expire)                   -- 第一次计数时设置过期时间
end
return count