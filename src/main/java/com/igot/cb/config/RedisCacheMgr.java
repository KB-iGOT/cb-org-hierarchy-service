package com.igot.cb.config;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.igot.cb.dto.Constants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PostConstruct;

@Component
public class RedisCacheMgr {

    private static int cache_ttl = 84600;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private JedisPool jedisDataPopulationPool;

    @Autowired
    ApplicationConfiguration configuration;

    ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger = LoggerFactory.getLogger(getClass().getName());

    @PostConstruct
    public void postConstruct() {
        if (!StringUtils.isEmpty(configuration.getRedisTimeout())) {
            cache_ttl = Integer.parseInt(configuration.getRedisTimeout());
        }
    }

    public void putCache(String key, Object object, int ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            String data = objectMapper.writeValueAsString(object);
            jedis.set(Constants.REDIS_COMMON_KEY + key, data);
            jedis.expire(Constants.REDIS_COMMON_KEY + key, ttl);
            logger.debug("Cache_key_value " + Constants.REDIS_COMMON_KEY + key + " is saved in redis");
        } catch (Exception e) {
            logger.error("Error occurced mget ", e);
        }
    }

    public void putCache(String key, Object object) {
        putCache(key, object, cache_ttl);
    }

    public void putStringInCache(String key, String value, int ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(Constants.REDIS_COMMON_KEY + key, value);
            jedis.expire(Constants.REDIS_COMMON_KEY + key, ttl);
            logger.debug("Cache_key_value " + Constants.REDIS_COMMON_KEY + key + " is saved in redis");
        } catch (Exception e) {
            logger.error("Error occurced mget ", e);
        }
    }

    public void putStringInCache(String key, String value) {
        putStringInCache(key, value, cache_ttl);
    }

    public boolean deleteKeyByName(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(Constants.REDIS_COMMON_KEY + key);
            logger.debug("Cache_key_value " + Constants.REDIS_COMMON_KEY + key + " is deleted from redis");
            return true;
        } catch (Exception e) {
            logger.error("Error occurced mget ", e);
            return false;
        }
    }

    public boolean deleteAllCBExtKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            String keyPattern = Constants.REDIS_COMMON_KEY + "*";
            Set<String> keys = jedis.keys(keyPattern);
            for (String key : keys) {
                jedis.del(key);
            }
            logger.info("All Keys starts with " + Constants.REDIS_COMMON_KEY + " is deleted from redis");
            return true;
        } catch (Exception e) {
            logger.error("Error occurced mget ", e);
            return false;
        }
    }

    public String getCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(Constants.REDIS_COMMON_KEY + key);
        } catch (Exception e) {
            logger.error("Error occurced mget ", e);
            return null;
        }
    }

    public List<String> mget(List<String> fields) {
        try (Jedis jedis = jedisPool.getResource()) {
            String[] updatedKeys = new String[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                updatedKeys[i] = Constants.REDIS_COMMON_KEY + fields.get(i);
            }
            return jedis.mget(updatedKeys);
        } catch (Exception e) {
            logger.error("Error occurced mget ", e);
        }
        return null;
    }

    public Set<String> getAllKeyNames() {
        try (Jedis jedis = jedisPool.getResource()) {
            String keyPattern = Constants.REDIS_COMMON_KEY + "*";
            return jedis.keys(keyPattern);
        } catch (Exception e) {
            logger.error("Error occurced getAllKeyNames ", e);
            return Collections.emptySet();
        }
    }

    public List<Map<String, Object>> getAllKeysAndValues() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        try (Jedis jedis = jedisPool.getResource()) {
            String keyPattern = Constants.REDIS_COMMON_KEY + "*";
            Map<String, Object> res = new HashMap<>();
            Set<String> keys = jedis.keys(keyPattern);
            if (!keys.isEmpty()) {
                for (String key : keys) {
                    Object entries;
                    entries = jedis.get(key);
                    res.put(key, entries);
                }
                result.add(res);
            }
        } catch (Exception e) {
            logger.error("Error occurced getAllKeysAndValues ", e);
            return Collections.emptyList();
        }
        return result;
    }

    public List<String> hget(String key, int index, String... fields) {
        try (Jedis jedis = jedisDataPopulationPool.getResource()) {
            jedis.select(index);
            return jedis.hmget(key, fields);
        } catch (Exception e) {
            logger.error("Error occurced hget ", e);
            return null;
        }
    }

    public String getCache(String key, Integer index) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (index != null) {
                jedis.select(index);
            }
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Error occurced getCache ", e);
            return null;
        }
    }

    public String getCacheFromDataRedish(String key, Integer index) {
        try (Jedis jedis = jedisDataPopulationPool.getResource()) {
            if (index != null) {
                jedis.select(index);
            }
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Error occurced getCacheFromDataRedish ", e);
            return null;
        }
    }

    public String getContentFromCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Error occurced getContentFromCache ", e);
            return null;
        }
    }

    public boolean keyExists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(Constants.REDIS_COMMON_KEY + key);
        } catch (Exception e) {
            logger.error("An Error Occurred while fetching value from Redis", e);
            return false;
        }
    }

    public boolean valueExists(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(Constants.REDIS_COMMON_KEY + key, value);
        } catch (Exception e) {
            logger.error("An Error Occurred while fetching value from Redis", e);
            return false;
        }
    }

    public void putCacheAsStringArray(String key, String[] values, Integer ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (null == ttl)
                ttl = cache_ttl;
            jedis.sadd(Constants.REDIS_COMMON_KEY + key, values);
            jedis.expire(Constants.REDIS_COMMON_KEY + key, ttl);
            logger.debug("Cache_key_value " + Constants.REDIS_COMMON_KEY + key + " is saved in redis");
        } catch (Exception e) {
            logger.error("An error occurred while saving data into Redis", e);
        }
    }
}
