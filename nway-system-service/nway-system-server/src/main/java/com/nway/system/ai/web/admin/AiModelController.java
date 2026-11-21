package com.nway.system.ai.web.admin;

import com.nway.system.ai.service.AiModelService;
import com.nway.system.ai.web.model.AiModelSaveRequest;
import com.nway.system.ai.web.model.AiModelVo;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型 Provider 配置：密钥加密存储，接口只返回是否已配置。
 *
 * @author nway
 */
@RequiresPermission(resource = "ai_interface")
@RestController
@RequestMapping("ai/models")
public class AiModelController {

    private final AiModelService modelService;

    public AiModelController(AiModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public Result<List<AiModelVo>> list() {
        return Result.ok(modelService.list());
    }

    @PostMapping
    public Result<AiModelVo> save(@RequestBody AiModelSaveRequest request) {
        return Result.ok(modelService.save(request));
    }

    @DeleteMapping("{id}")
    public Result<Void> delete(@PathVariable String id) {
        modelService.delete(id);
        return Result.ok();
    }
}
