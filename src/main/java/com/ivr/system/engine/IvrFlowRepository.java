package com.ivr.system.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ivr.system.model.IvrFlow;
import com.ivr.system.model.IvrNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IVR流程仓库 - 管理IVR流程定义
 */
@Slf4j
@Repository
public class IvrFlowRepository {

    private final Map<String, IvrFlow> flows = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Value("${ivr.flow.path:classpath:flows/*.yml}")
    private String flowPath;

    @PostConstruct
    public void init() {
        loadFlows();
    }

    /**
     * 加载所有流程定义
     */
    public void loadFlows() {
        log.info("Loading IVR flows from: {}", flowPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(flowPath);

            for (Resource resource : resources) {
                try {
                    loadFlow(resource);
                } catch (Exception e) {
                    log.error("Failed to load flow from {}: {}", resource.getFilename(), e.getMessage());
                }
            }

            log.info("Loaded {} IVR flows", flows.size());
        } catch (IOException e) {
            log.error("Failed to load flows: {}", e.getMessage());
        }
    }

    /**
     * 加载单个流程
     */
    private void loadFlow(Resource resource) throws IOException {
        log.debug("Loading flow from: {}", resource.getFilename());

        try (InputStream is = resource.getInputStream()) {
            IvrFlow flow = yamlMapper.readValue(is, IvrFlow.class);
            if (flow.getId() == null) {
                log.warn("Flow without ID in file: {}", resource.getFilename());
                return;
            }

            buildNodeMap(flow);

            flows.put(flow.getId(), flow);
            log.info("Loaded flow: {} ({})", flow.getName(), flow.getId());
        }
    }

    /**
     * 构建节点映射
     */
    private void buildNodeMap(IvrFlow flow) {
        if (flow.getNodes() == null) {
            return;
        }

        Map<String, IvrNode> nodeMap = new HashMap<>();
        for (IvrNode node : flow.getNodes()) {
            nodeMap.put(node.getId(), node);
        }
        flow.setNodeMap(nodeMap);
    }

    /**
     * 获取流程
     *
     * @param flowId 流程ID
     * @return 流程定义
     */
    public IvrFlow getFlow(String flowId) {
        return flows.get(flowId);
    }

    /**
     * 注册流程
     *
     * @param flow 流程定义
     */
    public void registerFlow(IvrFlow flow) {
        if (flow == null || flow.getId() == null) {
            throw new IllegalArgumentException("Flow or flow ID cannot be null");
        }

        buildNodeMap(flow);
        flows.put(flow.getId(), flow);
        log.info("Registered flow: {} ({})", flow.getName(), flow.getId());
    }

    /**
     * 移除流程
     *
     * @param flowId 流程ID
     */
    public void removeFlow(String flowId) {
        IvrFlow removed = flows.remove(flowId);
        if (removed != null) {
            log.info("Removed flow: {}", flowId);
        }
    }

    /**
     * 获取所有流程
     */
    public Map<String, IvrFlow> getAllFlows() {
        return new HashMap<>(flows);
    }

    /**
     * 重新加载流程
     */
    public void reloadFlows() {
        flows.clear();
        loadFlows();
    }

    /**
     * 检查流程是否存在
     */
    public boolean hasFlow(String flowId) {
        return flows.containsKey(flowId);
    }
}
