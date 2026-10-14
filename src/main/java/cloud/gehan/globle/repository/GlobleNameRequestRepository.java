package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.GlobleNameRequest;
import cloud.gehan.globle.model.NameRequestStatus;
import cloud.gehan.globle.model.Player;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * "Request a name change" rows. A player's own lookups take the whole {@link Player},
 * id and username together, as games' do; the admin's read everybody's.
 */
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public interface GlobleNameRequestRepository extends JpaRepository<GlobleNameRequest, Long> {

    List<GlobleNameRequest> findByUserIdAndUsernameAndStatus(long userId, String username, NameRequestStatus status);

    List<GlobleNameRequest> findByStatusOrderByCreatedAtDescIdDesc(NameRequestStatus status);

    List<GlobleNameRequest> findAllByOrderByCreatedAtDescIdDesc();

    long countByStatus(NameRequestStatus status);

    /** The player's requests still open, in no particular order. */
    default List<GlobleNameRequest> findOpen(Player player) {
        return findByUserIdAndUsernameAndStatus(player.id(), player.username(), NameRequestStatus.OPEN);
    }
}
