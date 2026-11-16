package uhsuhjupjup.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class NoteAsyncConfig {

    public static final String NOTE_DB_SCHEDULER = "noteDbScheduler";

    @Bean(name = NOTE_DB_SCHEDULER, destroyMethod = "dispose")
    public Scheduler noteDbScheduler(@Value("${note.async.db-threads:8}") int threads,
                                     @Value("${note.async.db-queue:200}") int queue) {
        return Schedulers.newBoundedElastic(threads, queue, "note-db");
    }
}
