package com.douding.file.controller.admin;

import com.alibaba.fastjson.JSON;
import com.douding.server.domain.Test;
import com.douding.server.dto.FileDto;
import com.douding.server.dto.ResponseDto;
import com.douding.server.enums.FileUseEnum;
import com.douding.server.exception.BusinessException;
import com.douding.server.exception.BusinessExceptionCode;
import com.douding.server.service.FileService;
import com.douding.server.service.TestService;
import com.douding.server.util.Base64ToMultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.util.List;

/*
    返回json 应用@RestController
    返回页面  用用@Controller
 */
@RequestMapping("/admin/file")
@RestController
public class UploadController {

    private static final Logger LOG = LoggerFactory.getLogger(UploadController.class);
    public  static final String BUSINESS_NAME ="文件上传";
    @Resource
    private TestService testService;

    @Value("${file.path}")
    private String FILE_PATH;

    @Value("${file.domain}")
    private String FILE_DOMAIN;

    @Resource
    private FileService fileService;

    @RequestMapping("/upload")
    public ResponseDto upload(@RequestBody FileDto fileDto) throws Exception {

        //由于前端将图片转化成Base64 传递，所以进行重转
        String shard = fileDto.getShard();
        MultipartFile multipartFile = Base64ToMultipartFile.base64ToMultipart(shard);

        //根据User获取枚举类中类别转小写
        String fileUser = FileUseEnum.getByCode(fileDto.getUse()).toString().toLowerCase();
        //建立path
        String path = fileUser + '/' + fileDto.getKey() + '.' + fileDto.getSuffix();

        //绝对路径
        String filePath = FILE_PATH + path;

        //分片路径 由于在前端有递归调用，这直接获取当前 ShardIndex
        String filePathShardIdx = filePath + '.' + fileDto.getShardIndex();
        //分片写入该路径
        File fileShard = new File(filePathShardIdx);
        multipartFile.transferTo(fileShard);

        //将file写入数据库
        fileDto.setPath(path);
        //此处的save中逻辑为 saveOrUpdate
        fileService.save(fileDto);

        //判断分片idx 是否等于 总数,是则上传完成，进行分片合并
        if(fileDto.getShardIndex()==fileDto.getShardTotal()){
            fileDto.setPath(filePath);
            merge(fileDto);
        }

        //返回数据给前端回显
        ResponseDto responseDto = new ResponseDto();
        //mvcConfig有映射
        //responseDto.setContent(fileDto);

        FileDto res = new FileDto();
        res.setPath(FILE_DOMAIN + path);
        responseDto.setContent(res);

        return responseDto;
    }

    //合并分片
    public void merge(FileDto fileDto) throws Exception {
        LOG.info("合并分片开始");
        //获取路径
        String path = fileDto.getPath();
        Integer fileDtoShardTotal = null;
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(path, true);
            //获取各个分片
            fileDtoShardTotal = fileDto.getShardTotal();
            for(int i=1;i<=fileDtoShardTotal;i++){
                FileInputStream fileInputStream = new FileInputStream(path + '.' + i);
                byte[] bytes = new byte[20 * 1024 * 1024];
                int len;
                while((len = fileInputStream.read(bytes)) != -1){
                    //追加
                    fileOutputStream.write(bytes,0,len);
                }
                fileInputStream.close();
            }
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //删除全部切片
        for(int i=1;i<=fileDtoShardTotal;i++){
            File file = new File(path + "." + i);
            file.delete();
        }
    }

    //可能传输中断进行进度检查
    @GetMapping("/check/{key}")
    public ResponseDto check(@PathVariable String key) throws Exception {
        LOG.info("检查上传分片开始：{}", key);
        ResponseDto responseDto = new ResponseDto();
        if(StringUtils.isEmpty(key)){
            throw new BusinessException(BusinessExceptionCode.FILE_KEY_ERROR);
        }
        //根据key获取 file
        FileDto fileDto = fileService.findByKey(key);
        //判断是否存在该file
        if(fileDto!=null){
            fileDto.setPath(FILE_DOMAIN + fileDto.getPath());
            responseDto.setContent(fileDto);
        }
        return responseDto;
    }

}//end class
