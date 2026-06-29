/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacossync.template.processor;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacossync.constant.TaskStatusEnum;
import com.alibaba.nacossync.dao.ClusterAccessService;
import com.alibaba.nacossync.dao.TaskAccessService;
import com.alibaba.nacossync.exception.SkyWalkerException;
import com.alibaba.nacossync.extension.SyncManagerService;
import com.alibaba.nacossync.extension.holder.NacosServerHolder;
import com.alibaba.nacossync.pojo.model.ClusterDO;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.alibaba.nacossync.pojo.request.TaskAddAllRequest;
import com.alibaba.nacossync.pojo.request.TaskAddRequest;
import com.alibaba.nacossync.pojo.result.TaskAddResult;
import com.alibaba.nacossync.template.Processor;
import com.alibaba.nacossync.util.SkyWalkerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Batch sync all services from a source Nacos cluster to a destination cluster.
 *
 * <p>Upgrade note: Previously this class used reflection to access the internal
 * {@code NamingProxy} of {@code NacosNamingService} and called the v1 HTTP API
 * {@code /catalog/services}. Since Nacos 3.x removed v1/v2 HTTP APIs and
 * restructured internal classes, we now use the public API
 * {@link NamingService#getServicesOfServer(int, int)} to page through all
 * services of the source cluster.</p>
 *
 * @author NacosSync
 * @version $Id: TaskAddAllProcessor.java, v 0.1 2022-03-23 PM11:40 NacosSync Exp $$
 */
@Slf4j
@Service
public class TaskAddAllProcessor implements Processor<TaskAddAllRequest, TaskAddResult> {
    
    private static final String CONSUMER_PREFIX = "consumers:";
    
    private static final int PAGE_SIZE = 1000;
    
    private final NacosServerHolder nacosServerHolder;
    
    private final SyncManagerService syncManagerService;
    
    private final TaskAccessService taskAccessService;
    
    private final ClusterAccessService clusterAccessService;
    
    public TaskAddAllProcessor(NacosServerHolder nacosServerHolder, SyncManagerService syncManagerService,
            TaskAccessService taskAccessService, ClusterAccessService clusterAccessService) {
        this.nacosServerHolder = nacosServerHolder;
        this.syncManagerService = syncManagerService;
        this.taskAccessService = taskAccessService;
        this.clusterAccessService = clusterAccessService;
    }
    
    @Override
    public void process(TaskAddAllRequest addAllRequest, TaskAddResult taskAddResult, Object... others)
            throws Exception {
        
        ClusterDO destCluster = clusterAccessService.findByClusterId(addAllRequest.getDestClusterId());
        
        ClusterDO sourceCluster = clusterAccessService.findByClusterId(addAllRequest.getSourceClusterId());
        
        if (Objects.isNull(destCluster) || Objects.isNull(sourceCluster)) {
            throw new SkyWalkerException("Please check if the source or target cluster exists.");
        }
        
        if (Objects.isNull(syncManagerService.getSyncService(sourceCluster.getClusterId(), destCluster.getClusterId()))) {
            throw new SkyWalkerException("current sync type not supported.");
        }
        // TODO 目前仅支持 Nacos 为源的同步类型，待完善更多类型支持。
        final NamingService sourceNamingService = nacosServerHolder.get(sourceCluster.getClusterId());
        if (sourceNamingService == null) {
            throw new SkyWalkerException("only support sync type that the source of the Nacos.");
        }
        
        final List<String> allServiceNames = listAllServiceNames(sourceNamingService);
        if (allServiceNames.isEmpty()) {
            throw new SkyWalkerException("sourceCluster data empty");
        }
        
        for (String serviceName : allServiceNames) {
            // exclude subscriber
            if (addAllRequest.isExcludeConsumer() && serviceName.startsWith(CONSUMER_PREFIX)) {
                continue;
            }
            TaskAddRequest taskAddRequest = new TaskAddRequest();
            taskAddRequest.setSourceClusterId(sourceCluster.getClusterId());
            taskAddRequest.setDestClusterId(destCluster.getClusterId());
            taskAddRequest.setServiceName(serviceName);
            this.dealTask(addAllRequest, taskAddRequest);
        }
    }
    
    /**
     * Page through all services in the source cluster using the public NamingService API.
     *
     * <p>Replaces the previous reflective call to {@code NamingProxy.reqApi("/catalog/services")}
     * which relied on the v1 HTTP API removed in Nacos 3.2.0+. The public API
     * {@link NamingService#getServicesOfServer(int, int)} is compatible with
     * nacos-client 1.x / 2.x / 3.x and uses gRPC under the hood in 2.x+.</p>
     *
     * @param namingService source cluster naming service
     * @return all service names in the source cluster
     * @throws NacosException if listing services fails
     */
    private List<String> listAllServiceNames(NamingService namingService) throws NacosException {
        int pageNo = 1;
        ListView<String> page = namingService.getServicesOfServer(pageNo, PAGE_SIZE);
        List<String> allServiceNames = page.getData();
        // Loop until a page has fewer than PAGE_SIZE items (last page).
        while (page.getData() != null && page.getData().size() >= PAGE_SIZE) {
            pageNo++;
            page = namingService.getServicesOfServer(pageNo, PAGE_SIZE);
            if (page.getData() != null) {
                allServiceNames.addAll(page.getData());
            }
        }
        return allServiceNames;
    }
    
    private void dealTask(TaskAddAllRequest addAllRequest, TaskAddRequest taskAddRequest) throws Exception {
        
        String taskId = SkyWalkerUtil.generateTaskId(taskAddRequest);
        TaskDO taskDO = taskAccessService.findByTaskId(taskId);
        if (null == taskDO) {
            taskDO = new TaskDO();
            taskDO.setTaskId(taskId);
            taskDO.setDestClusterId(addAllRequest.getDestClusterId());
            taskDO.setSourceClusterId(addAllRequest.getSourceClusterId());
            taskDO.setServiceName(taskAddRequest.getServiceName());
            taskDO.setVersion(taskAddRequest.getVersion());
            taskDO.setGroupName(taskAddRequest.getGroupName());
            taskDO.setNameSpace(taskAddRequest.getNameSpace());
            taskDO.setTaskStatus(TaskStatusEnum.SYNC.getCode());
            taskDO.setWorkerIp(SkyWalkerUtil.getLocalIp());
            taskDO.setOperationId(SkyWalkerUtil.generateOperationId());
            
        } else {
            taskDO.setTaskStatus(TaskStatusEnum.SYNC.getCode());
            taskDO.setOperationId(SkyWalkerUtil.generateOperationId());
        }
        taskAccessService.addTask(taskDO);
    }
    
}
