package com.flashsale.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLuaConfig {

    /**
     * Lua script: atomic check-and-decrement stock.
     *
     * KEYS[1] = stock:available:{variantId}
     * ARGV[1] = quantity to decrement
     *
     * Returns:
     *   1  -> success (decremented)
     *   0  -> insufficient stock (current value is less than requested)
     *  -1  -> key not found in Redis (cache miss)
     */
    public static final String STOCK_DECREMENT_LUA = """
            local cur = redis.call('GET', KEYS[1])
            if not cur then return -1 end
            local n = tonumber(cur)
            local q = tonumber(ARGV[1])
            if n < q then return 0 end
            redis.call('DECRBY', KEYS[1], q)
            return 1
            """;

    /**
     * Lua script: atomic increment stock.
     *
     * KEYS[1] = stock:available:{variantId}
     * ARGV[1] = quantity to add
     *
     * Returns the new stock value (Long).
     * If the key is missing, Redis creates it with value 0 before increment.
     */
    public static final String STOCK_INCREMENT_LUA = """
            return redis.call('INCRBY', KEYS[1], ARGV[1])
            """;

    @Bean(name = "stockDecrementScript")
    public DefaultRedisScript<Long> stockDecrementScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(STOCK_DECREMENT_LUA);
        script.setResultType(Long.class);
        return script;
    }

    @Bean(name = "stockIncrementScript")
    public DefaultRedisScript<Long> stockIncrementScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(STOCK_INCREMENT_LUA);
        script.setResultType(Long.class);
        return script;
    }
}
