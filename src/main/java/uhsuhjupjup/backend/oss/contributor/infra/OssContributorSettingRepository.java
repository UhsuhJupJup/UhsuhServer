package uhsuhjupjup.backend.oss.contributor.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;

public interface OssContributorSettingRepository extends JpaRepository<OssContributorSetting, Long> {
}
