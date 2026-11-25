package com.eyeqlytics.fieldops.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Demo data so reviewers can log in immediately. All passwords: Password@123 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final ZoneRepository zones;
    private final TeamRepository teams;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;

        Zone east = zone("Zone-East", "Odisha");
        Zone west = zone("Zone-West", "Odisha");
        Team teamE = team("East Maintenance Crew", east.getId());
        team("West Maintenance Crew", west.getId());

        user("Admin", "admin@fieldops.com", Role.ADMIN, null, null);
        user("Priya Planner", "planner@fieldops.com", Role.PLANNER, east.getId(), null);
        user("Sam Supervisor", "supervisor@fieldops.com", Role.SUPERVISOR, east.getId(), null);
        user("Tara Tech", "tech1@fieldops.com", Role.TECHNICIAN, east.getId(), teamE.getId());
        user("Tom Tech", "tech2@fieldops.com", Role.TECHNICIAN, east.getId(), teamE.getId());
        user("Tia Tech", "tech3@fieldops.com", Role.TECHNICIAN, east.getId(), teamE.getId());
        user("Riya Requester", "requester@fieldops.com", Role.REQUESTER, east.getId(), null);
        log.info("Seeded demo data (password for all users: Password@123)");
    }

    private Zone zone(String name, String region) {
        Zone z = new Zone(); z.setName(name); z.setRegion(region); return zones.save(z);
    }
    private Team team(String name, Long zoneId) {
        Team t = new Team(); t.setName(name); t.setZoneId(zoneId); return teams.save(t);
    }
    private void user(String name, String email, Role role, Long zoneId, Long teamId) {
        User u = new User();
        u.setFullName(name); u.setEmail(email); u.setRole(role);
        u.setZoneId(zoneId); u.setTeamId(teamId);
        u.setPasswordHash(encoder.encode("Password@123"));
        users.save(u);
    }
}
