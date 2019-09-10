### 系统访问日志记录工具
本工具会根据系统配置的数据库地址生成一张`system_log`表用于记录，如介意，请慎用，表字段如下：

| 字段              | 释义                          |
|------------------|-----------------------------|
| id               | 记录id 自增生成                   |
| request_ip      | 请求IP                        |
| uri              | 请求uri                       |
| method           | 请求方式（GET、POST、PUT、DELETE……） |
| param            | 请求参数                        |
| response         | 响应结果                        |
| response\_status | 响应状态码                       |
| create\_date     | 记录生成时间  本次请求时间              |



#### 本工具提供了如下接口，方便开发者调用：

- 获取指定ip的访问记录

    - 接口地址： /log/getLogByIp
    - 请求方式： GET
    - 参数：
    
        | 参数 | 是否必传 | 备注   |
        |----|------|------|
        | IP | 是    | 请求IP |
        
    - 响应结果
    ```
        [
          {
            "URoleQuery": {
              "uPermissions": [],
              "uRole": {
                "id": 3,
                "name": "你好1231"
              }
            }
          }
        ]
    ```

    


