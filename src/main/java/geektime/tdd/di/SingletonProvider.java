package geektime.tdd.di;

import java.util.List;

public class SingletonProvider<T> implements ComponentProvider<T> {

    private ComponentProvider<T> provider;
    private T singleton;

    public SingletonProvider(ComponentProvider<T> provider) {
        this.provider = provider;
    }

    @Override
    public T get(Context context) {
        if (singleton == null) {
            singleton = provider.get(context);
        }
        return singleton;
    }

    @Override
    public List<ComponentRef<?>> getDependencies() {
        return provider.getDependencies();
    }
}
