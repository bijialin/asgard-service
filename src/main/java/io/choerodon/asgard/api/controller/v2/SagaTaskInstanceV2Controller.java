package io.choerodon.asgard.api.controller.v2;

import io.choerodon.asgard.api.vo.SagaTaskInstance;
import io.choerodon.asgard.app.eventhandler.SagaInstanceHandler;
import io.choerodon.asgard.app.eventhandler.SagaInstanceEventPublisher;
import io.choerodon.asgard.app.service.SagaTaskInstanceService;
import io.choerodon.asgard.saga.dto.PollSagaTaskInstanceDTO;
import io.choerodon.core.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import javax.validation.Valid;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by hailuo.liu@hand-china.com on 2019/5/15.
 */
@RestController
@RequestMapping("/v1/ext/sagas/tasks/instances")
public class SagaTaskInstanceV2Controller {
    private static final Logger LOGGER = LoggerFactory.getLogger(SagaTaskInstanceV2Controller.class);
    private SagaTaskInstanceService sagaTaskInstanceService;
    private SagaInstanceHandler sagaInstanceHandler;

    public SagaTaskInstanceV2Controller(SagaTaskInstanceService sagaTaskInstanceService, SagaInstanceHandler sagaInstanceHandler) {
        this.sagaTaskInstanceService = sagaTaskInstanceService;
        this.sagaInstanceHandler = sagaInstanceHandler;
    }

    public void setSagaTaskInstanceService(SagaTaskInstanceService sagaTaskInstanceService) {
        this.sagaTaskInstanceService = sagaTaskInstanceService;
    }

    @PostMapping("/poll")
    @Permission(permissionWithin = true)
    @ApiOperation(value = "内部接口。拉取指定code的任务列表，并更新instance的值")
    public DeferredResult<ResponseEntity<Set<SagaTaskInstance>>> pollBatch(@RequestBody @Valid final PollSagaTaskInstanceDTO pollBatchDTO) {
        LOGGER.info("poll SagaTaskInstance from {},instance: {},maxPollSize:{}", pollBatchDTO.getService(), pollBatchDTO.getInstance(), pollBatchDTO.getMaxPollSize());
        if (!CollectionUtils.isEmpty(pollBatchDTO.getRunningIds())) {
            LOGGER.info("RunningIds size:{}", pollBatchDTO.getRunningIds().size());
        }

        if (pollBatchDTO.getMaxPollSize() == null) {
            pollBatchDTO.setMaxPollSize(500);
        }
        DeferredResult<ResponseEntity<Set<SagaTaskInstance>>> deferredResult = new DeferredResult<>(60000l);
        deferredResult.onTimeout(() -> {
                    deferredResult.setResult(new ResponseEntity<>(ConcurrentHashMap.newKeySet(), HttpStatus.OK));
                    sagaInstanceHandler.removeDeferredResult(SagaInstanceEventPublisher.TAST_INSTANCE_PREFIX,pollBatchDTO.getService(), deferredResult);
                }
        );
        Set<SagaTaskInstance> pollBatch = sagaTaskInstanceService.pollBatch(pollBatchDTO);
        if (!CollectionUtils.isEmpty(pollBatch)) {
            LOGGER.info("pollBatch size:{}", pollBatch.size());
            deferredResult.setResult(new ResponseEntity<>(pollBatch, HttpStatus.OK));
        } else {
            sagaInstanceHandler.addDeferredResult(SagaInstanceEventPublisher.TAST_INSTANCE_PREFIX,pollBatchDTO.getService(), deferredResult);
        }
        return deferredResult;
    }
}
