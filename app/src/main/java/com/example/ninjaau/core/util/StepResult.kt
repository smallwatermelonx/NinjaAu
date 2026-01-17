package com.example.ninjaau.core.util

/**
 * 业务步骤执行结果（所有步骤统一返回这个格式）
 * @param T 步骤返回的数据类型（比如坐标Pair、Boolean等）
 * @property isSuccess 步骤是否执行成功
 * @property data 步骤成功时返回的数据（比如按钮坐标）
 * @property errorMsg 步骤失败时的错误信息
 */
data class StepResult<T>(
    val isSuccess: Boolean,
    val data: T? = null,
    val errorMsg: String? = null
)