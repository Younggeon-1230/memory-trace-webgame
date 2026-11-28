package com.metamong.memorytrace.auth.repository;

import com.metamong.memorytrace.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
