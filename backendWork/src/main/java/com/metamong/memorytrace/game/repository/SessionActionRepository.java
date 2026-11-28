package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.SessionAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionActionRepository extends JpaRepository<SessionAction, Long> {
}
