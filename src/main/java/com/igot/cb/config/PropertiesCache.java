package com.igot.cb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesCache {

    private static Logger logger = LoggerFactory.getLogger(Neo4jConfig.class);
    private final String fileName = "application.properties";
    private final Properties configProp = new Properties();
    private static PropertiesCache instance;

    /** private default constructor */
    private PropertiesCache() {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(fileName);
        try {
            configProp.load(in);
        } catch (IOException e) {
            logger.error("Error in properties cache", e);
        }
    }

    public static PropertiesCache getInstance() {
        if (null == instance) {
            synchronized (PropertiesCache.class) {
                if (instance == null) {
                    instance = new PropertiesCache();
                }
            }
        }
        return instance;
    }

    /**
     * Method to read value from resource file and if key not found then by default return key values
     * itself.
     *
     * @param key
     * @return
     */
    public String getProperty(String key) {
        return configProp.getProperty(key) != null ? configProp.getProperty(key) : null;
    }

    /**
     * Method to read value from resource file .
     *
     * @param key
     * @return
     */
    public String readProperty(String key) {
        return configProp.getProperty(key);
    }

}
