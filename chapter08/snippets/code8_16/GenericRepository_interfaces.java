@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {
    default T getRequired(ID id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("实体不存在: " + id));
    }
}

public interface StudentRepository extends BaseRepository<Student, Long> {
    Optional<Student> findByStudentNo(String studentNo);
}
