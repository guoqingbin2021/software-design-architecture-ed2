package demo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

record Actor(String name, Set<String> stores, boolean finance) {}

final class CurrentContext {
    // 示例中的门店分配保存在服务端，客户端只提交待核验的门店编号。
    private final Map<String, Set<String>> assignments =
            Map.of("manager-a", Set.of("STORE-A"), "finance-a", Set.of("STORE-A"));

    Actor currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("缺少已认证身份");
        }
        boolean finance =
                auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_FINANCE"::equals);
        return new Actor(
                auth.getName(), assignments.getOrDefault(auth.getName(), Set.of()), finance);
    }
}

final class Scope {
    String requireAllowedStore(Actor actor, String requestedStore) {
        if (requestedStore == null || !actor.stores().contains(requestedStore)) {
            throw new AccessDeniedException("该门店不在授权范围内");
        }
        return requestedStore;
    }
}

final class FieldPolicy {
    List<String> allowedFields(Actor actor) {
        if (actor.finance()) {
            return List.of("sku", "name", "priceCents", "costCents", "supplier", "phone", "email");
        }
        return List.of("sku", "name", "priceCents");
    }
}
