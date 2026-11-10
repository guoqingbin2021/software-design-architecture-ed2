package demo;

import java.util.List;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class Identity {
    public static void setIdentity(boolean allowed) {
        var authorities =
                allowed
                        ? List.of(new SimpleGrantedAuthority("queryPriceForm"))
                        : List.<SimpleGrantedAuthority>of();
        var auth =
                new UsernamePasswordAuthenticationToken(
                        "门店A店长", "unused", authorities); // 构造教学用的已认证身份。
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
