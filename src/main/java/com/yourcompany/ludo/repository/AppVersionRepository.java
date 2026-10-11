public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {
    Optional<AppVersion> findTopByOrderByVersionCodeDesc();
}
