package org.springblade.modules.index.controller;

import lombok.extern.slf4j.Slf4j;
import org.springblade.modules.index.service.IndexCustomSqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 使用示例
 */
@Slf4j
@RestController
@RequestMapping("/api/index")
public class IndexController {
    
    @Autowired
    private IndexCustomSqlService indexCustomSqlService;
    
    /**
     * 示例1：使用死锁重试机制删除数据
     */
    @PostMapping("/delete")
    public void deletePerformanceDetails(
            @RequestParam Long indexId,
            @RequestParam String indexParams) {
        
        try {
            // 使用带重试机制的方法
            int deleted = indexCustomSqlService.updateCustomSqlWithRetry(
                indexId, 
                indexParams, 
                300  // 每次删除300条
            );
            
            log.info("成功删除{}条记录", deleted);
        } catch (Exception e) {
            log.error("删除操作失败", e);
            throw new RuntimeException("删除失败", e);
        }
    }
    
    /**
     * 示例2：使用分批删除（推荐用于大数据量）
     */
    @PostMapping("/delete-batch")
    public void deletePerformanceDetailsInBatches(
            @RequestParam Long indexId,
            @RequestParam String indexParams) {
        
        try {
            // 分批删除，每批100条，最多10批
            int totalDeleted = indexCustomSqlService.updateCustomSqlInBatches(
                indexId,
                indexParams,
                100,  // 每批100条
                10    // 最多10批（即最多删除1000条）
            );
            
            log.info("分批删除完成，共删除{}条记录", totalDeleted);
        } catch (Exception e) {
            log.error("分批删除操作失败", e);
            throw new RuntimeException("分批删除失败", e);
        }
    }
}
