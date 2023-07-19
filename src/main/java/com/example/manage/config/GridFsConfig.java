package com.example.manage.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;


/**
 * @author jensen
 */
@Configuration
public class GridFsConfig {


    //获取配置文件中数据库信息
    @Value("${spring.data.mongodb.database}")
    String db;

    @Bean(name = "gridFsMp4Template")
    public GridFsTemplate gridFsTestTemplate(MongoDatabaseFactory dbFactory, MongoConverter converter) {
        return new GridFsTemplate(dbFactory, converter, "video_test");
    }


    //GridFSBucket用于打开下载流
    @Bean(name = "gridMp4BucketTemplate")
    public GridFSBucket getGridFSBucket(MongoClient mongoClient) {
        MongoDatabase mongoDatabase = mongoClient.getDatabase(db);
        GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, "video_test");
        return bucket;
    }
}
