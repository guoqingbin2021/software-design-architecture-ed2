package edu.textbook.selection.security;

import edu.textbook.selection.repository.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    public DatabaseUserDetailsService(UserAccountRepository users,StudentRepository students,TeacherRepository teachers) {
        this.users=users; this.students=students; this.teachers=teachers;
    }
    @Override @Transactional(readOnly=true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user=users.findById(username).orElseThrow(()->new UsernameNotFoundException("账户不存在"));
        String name=switch(user.getRole()) {
            case "STUDENT" -> students.findById(user.getPersonId()).orElseThrow(()->new UsernameNotFoundException("学生不存在")).getName();
            case "TEACHER" -> teachers.findById(user.getPersonId()).orElseThrow(()->new UsernameNotFoundException("教师不存在")).getName();
            default -> throw new UsernameNotFoundException("账户角色无效");
        };
        return new Actor(user.getUsername(),user.getPasswordHash(),user.getRole(),user.getPersonId(),name);
    }
}
