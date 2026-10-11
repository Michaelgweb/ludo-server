@Entity
@Table(name = "app_versions")
public class AppVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String versionName;
    private int versionCode;
    private boolean forceUpdate;
    private String apkUrl;
    private LocalDateTime createdAt = LocalDateTime.now();
    // getters & setters
}
