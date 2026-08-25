package cloud.gehan.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;
    private String role;

    /**
     * An account with no password stored has never been claimed. The next successful
     * login saves whatever password was typed. Admins put an account back into this
     * state by resetting it.
     */
    public boolean isUnclaimed() {
        return password == null || password.isBlank();
    }
}
