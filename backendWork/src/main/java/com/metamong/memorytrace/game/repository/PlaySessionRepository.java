package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.PlaySession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaySessionRepository extends JpaRepository<PlaySession, Long> {
}
