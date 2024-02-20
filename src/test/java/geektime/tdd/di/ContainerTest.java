
package geektime.tdd.di;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;

public class ContainerTest {
    ContextConfig config;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
    }

    @Nested
    public class DependenciesSelection {
        @Nested
        public class ProviderType{
        }
    }

    @Nested
    public class LifecycleManagement {
    }
}

interface Component {
    default Dependency dependency(){return null;}
}

interface Dependency {
}

interface AnotherDependency {
}



