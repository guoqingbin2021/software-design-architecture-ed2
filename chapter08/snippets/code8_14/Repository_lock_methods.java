// StudentRepository中的方法
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Student s where s.id = :id")
Optional<Student> findForUpdate(@Param("id") Long id);

// TeachingClassRepository中的方法
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select c from TeachingClass c where c.id = :id")
Optional<TeachingClass> findForUpdate(@Param("id") Long id);
