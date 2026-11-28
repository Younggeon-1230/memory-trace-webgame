package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.SessionSuspect;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSuspectRepository extends JpaRepository<SessionSuspect, Long> {
}
