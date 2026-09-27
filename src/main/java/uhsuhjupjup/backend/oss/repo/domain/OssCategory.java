package uhsuhjupjup.backend.oss.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;

@Entity
@Table(name = "oss_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name_ko", nullable = false, length = 60)
    private String nameKo;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    private OssCategory(String code, String nameKo, String nameEn) {
        this.code = code;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
    }

    public static OssCategory create(String code, String nameKo, String nameEn) {
        return new OssCategory(code, nameKo, nameEn);
    }
}
