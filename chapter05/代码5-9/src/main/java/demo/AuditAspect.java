package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

@Aspect
@Order(0) // 本例让审计包在授权检查外层。
public class AuditAspect {
    @Around("execution(* demo.SecuredPriceService.queryPriceForm(..))")
    public Object record(ProceedingJoinPoint call) throws Throwable {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String user = auth == null ? "anonymous" : auth.getName();
        long start = System.nanoTime();
        String outcome = "失败", error = "-";
        try {
            Object result = call.proceed(); // 将调用交给后续处理链。
            outcome = "成功"; // 只有正常返回才记成功。
            return result;
        } catch (AccessDeniedException ex) {
            outcome = "拒绝";
            error = ex.getClass().getSimpleName();
            throw ex; // 将拒绝异常继续交给调用者。
        } catch (Throwable ex) {
            error = ex.getClass().getSimpleName();
            throw ex;
        } finally { // 成功、拒绝和失败都在此记录。
            long nanos = System.nanoTime() - start;
            System.out.printf(
                    "审计：用户=%s，操作=查询调价单，结果=%s，" + "异常=%s，耗时=%dns%n", user, outcome, error, nanos);
            // 只记调用信息和异常类型，不输出单据内容。
        }
    }
}
