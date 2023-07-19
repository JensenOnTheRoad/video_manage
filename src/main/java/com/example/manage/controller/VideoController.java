package com.example.manage.controller;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * @author jensen
 */
@Slf4j
@RestController
@RequestMapping("/video")
public class VideoController {
    @Resource(name = "gridFsMp4Template")
    private GridFsTemplate gridFsTestTemplate;

    @Resource(name = "gridMp4BucketTemplate")
    private GridFSBucket gridFsBucket;

    /**
     * 上传并存储文件
     *  TODO 单线程性能较差，而且可能会阻塞，后面优化成多线程
     */
    @PostMapping("/upload")
    @SneakyThrows
    public void upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传失败，请选择文件");
        }
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElseThrow(() -> new IllegalArgumentException("上传失败，获取文件名失败！"));
        InputStream inputStream = file.getInputStream();
        String contentType = file.getContentType();
        ObjectId objectId = gridFsTestTemplate.store(inputStream, fileName, contentType);
        String id = objectId.toString();
        //获取到文件的id，可以从数据库中查找
        log.info("Grip fs id : {}", id);
    }


    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public void deleted(@PathVariable("id") String id) {
        Query query = Query.query(Criteria.where("_id").is(new ObjectId(id)));
        gridFsTestTemplate.delete(query);
    }

    /**
     * 在线分段读取视频文件
     */
    @GetMapping("/view/{id}")
    public void viewTo(HttpServletResponse response, HttpServletRequest request, @PathVariable("id") String id) throws Exception {
        Query gridQuery = new Query().addCriteria(Criteria.where("_id").is(new ObjectId(id)));
        //根据id查询文件
        GridFSFile gridFsdbFile = gridFsTestTemplate.findOne(gridQuery);

        response.setHeader("Content-Type", "video/mp4");
        Long fileLength = Optional.ofNullable(gridFsdbFile).map(GridFSFile::getLength).orElse(0L);
        long skip = -1;
        long length = -1;
        long end = fileLength - 1;
        String range = request.getHeader("Range");
        if (range != null && range.length() > 0) {
            int idx = range.indexOf("-");
            skip = Long.parseLong(range.substring(6, idx));
            if ((idx + 1) < range.length()) {
                end = Long.parseLong(range.substring(idx + 1));
            }
            length = end - skip + 1;
        }

        if (range == null || range.length() == 0) {
            response.setHeader("Content-Length", String.valueOf(fileLength));
            response.setStatus(200);
        } else {
            response.setHeader("Content-Length", String.valueOf(length));
            response.setHeader("Content-Range", "bytes " + skip + "-" + end + "/" + fileLength);
            response.setStatus(206);
        }
        log.info("bytes %d-%d/%d".formatted(skip, end, fileLength));
        assert gridFsdbFile != null;
        download(response.getOutputStream(), gridFsdbFile, skip, length);
    }

    /**
     * 文件下载基础类
     * 断点续读
     *
     * @param outputStream 文件输出流
     * @param fsFile       mongo文件
     * @param skip         跳过多少字节 <=0忽略
     * @param length       输出字节长度 <=忽略
     */
    @SneakyThrows
    public void download(OutputStream outputStream, GridFSFile fsFile, long skip, long length) {
        InputStream inputStream = null;
        try {
            //打开流下载对象
            GridFSDownloadStream in = gridFsBucket.openDownloadStream(fsFile.getObjectId());
            //获取流对象
            GridFsResource resource = new GridFsResource(fsFile, in);
            inputStream = resource.getInputStream();
            if (skip > 0) {
                long skipped = inputStream.skip(skip);
                log.info("skipped : {}", skipped);
            }
            byte[] bs = new byte[1024];
            int len;
            while ((len = inputStream.read(bs)) != -1) {
                if (length > 0) {
                    if (length > len) {
                        outputStream.write(bs, 0, len);
                        outputStream.flush();
                        length -= len;
                    } else {
                        outputStream.write(bs, 0, (int) length);
                        outputStream.flush();
                        break;
                    }
                } else {
                    outputStream.write(bs, 0, len);
                    outputStream.flush();
                }
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }
}
