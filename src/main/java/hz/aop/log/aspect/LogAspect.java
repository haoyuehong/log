package hz.aop.log.aspect;


import com.alibaba.fastjson.JSON;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author haoyuehong
 */
@Aspect
@Component
@Slf4j
public class LogAspect {

    @Value("${spring.datasource.driver-class-name}")
    private String JDBC_DRIVER;
    @Value("${spring.datasource.url}")
    private  String DB_URL;
    @Value("${spring.datasource.username}")
    private String USERNAME;
    @Value("${spring.datasource.password}")
    private String PASSWORD;


    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public void pointCut(){

    }

    @Around("pointCut()")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        //IP地址
        String ipAddr = getRemoteHost(request);
        String uri = request.getRequestURI();
        String reqParam = preHandle(joinPoint,request);
        String requestMethod = requestMethod(request);
        Object result= joinPoint.proceed();
        int status = 0;
        if(response != null){
            status = response.getStatus();
        }

        String respParam = postHandle(result);
        if(!validateTableNameExist("system_log")){
            createTable();
        }
        insertLog(ipAddr,uri,reqParam,requestMethod,status,respParam);
        return result;
    }

    public boolean validateTableNameExist(String tableName) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
            ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null);
            if (rs.next()) {
                return true;
            }else {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;

    }


    private void createTable(){
        String[] split = DB_URL.split(":");
        Connection conn = null;
        Statement stmt = null;
        try{
            //STEP 2: Register JDBC driver
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
            stmt = conn.createStatement();
            String sql;
            if("mysql".equals(split[1])){
                sql = "CREATE TABLE system_log(" +
                        "  `id` int(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                        "  `request_ip` varchar(255) NULL COMMENT '请求ip'," +
                        "  `uri` varchar(255) NULL COMMENT '请求uri'," +
                        "  `method` varchar(255) NULL COMMENT '请求方法'," +
                        "  `param` text NULL COMMENT '请求参数'," +
                        "  `response` text NULL COMMENT '请求返回结果'," +
                        "  `response_status` int(255) NULL COMMENT '响应状态码'," +
                        "  `create_date` datetime NULL COMMENT '创建时间'," +
                        "  PRIMARY KEY (`id`)" +
                        ");";
            }else{
                sql = "CREATE TABLE system_log(" +
                        "  [id] int IDENTITY(1,1) NOT NULL," +
                        "  [request_ip] varchar(255)," +
                        "  [uri] varchar(255)," +
                        "  [method] varchar(255)," +
                        "  [param] text," +
                        "  [response] text," +
                        "  [response_status] varchar(255)," +
                        "  [create_date] datetime2," +
                        "  PRIMARY KEY CLUSTERED ([id])n" +
                        "WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)" +
                        ")";
            }


            stmt.executeUpdate(sql);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try{
                if(stmt!=null){
                    stmt.close();
                }
                if(conn!=null){
                    conn.close();
                }
            }catch(SQLException se){

            }
        }
    }


    /**
     * 插入数据
     * @param ipAddr ip
     * @param uri uri
     * @param reqParam  请求参数
     * @param requestMethod 请求方法
     * @param status    请求状态
     * @param respParam 请求结果
     */
    private void insertLog(String ipAddr,String uri,String reqParam,String requestMethod,int status,String respParam){
        Connection conn = null;
        PreparedStatement stmt = null;
        try{
            //STEP 2: Register JDBC driver
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
            String sql = "insert into system_log (request_ip,uri,method,param,response,response_status,create_date) values (?,?,?,?,?,?,?)";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,ipAddr);
            stmt.setString(2,uri);
            stmt.setString(3,requestMethod);
            stmt.setString(4,reqParam);
            stmt.setString(5,respParam);
            stmt.setInt(6,status);
            stmt.setTimestamp(7,Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try{
                if(stmt!=null){
                    stmt.close();
                }
                if(conn!=null){
                    conn.close();
                }
            }catch(SQLException se){

            }
        }
    }


    /**
     * 获取请求方式
     * @param request  请求
     * @return 返回
     */
    private String requestMethod(HttpServletRequest request){
        return request.getMethod();
    }


    /**
     * 入参数据
     * @param joinPoint 切点
     * @param request 请求
     * @return
     */
    private String preHandle(ProceedingJoinPoint joinPoint,HttpServletRequest request) throws IOException {

        String reqParam = "";
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method targetMethod = methodSignature.getMethod();
        Annotation[] annotations = targetMethod.getAnnotations();
        for (Annotation annotation : annotations) {
            //此处可以改成自定义的注解
            if (annotation.annotationType().equals(RequestMapping.class)) {
                System.out.println(request.getContentType());
                if("application/json".equals(request.getContentType())){
                    reqParam = resolveJsonRequest(joinPoint);
                }else {
                    reqParam = JSON.toJSONString(request.getParameterMap());
                }

                break;
            }
        }
        return reqParam;
    }

    public String resolveJsonRequest(ProceedingJoinPoint joinPoint) {
        List<Map<String,Object>> list  = new ArrayList<>();
        Object[] args = joinPoint.getArgs();
        for(Object obj : args){
            if(obj == null || obj instanceof HttpServletRequest || obj instanceof HttpServletResponse){
                continue;
            }
            Map<String,Object> map = new HashMap<>();
            map.put(obj.getClass().getSimpleName(),obj);
            list.add(map);
        }
        if(list.size() > 1){
            return JSON.toJSONString(list);
        }else{
            return JSON.toJSONString(list.get(0));
        }

    }


    /**
     * 返回数据
     * @param retVal  返回结果
     * @return 格式化后的返回接货
     */
    private String postHandle(Object retVal) {
        if(null == retVal){
            return "";
        }
        return JSON.toJSONString(retVal);
    }


    /**
     * 获取目标主机的ip
     * @param request  请求
     * @return  IP
     */
    private String getRemoteHost(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }


}
