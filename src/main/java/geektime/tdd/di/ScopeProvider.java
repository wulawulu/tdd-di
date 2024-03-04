package geektime.tdd.di;

interface ScopeProvider {
    ComponentProvider<?> create(ComponentProvider<?> componentProvider);
}
