package fr.laas.fape.structures;

import java.util.Arrays;

public class HStructs {

    @Ident(Top.class)
    public static abstract class Top extends AbsIdentifiable {

    }

    @Ident(Top.class)
    public static class BotInt extends Top {
        static Desc<BotInt> desc = Desc.get(BotInt.class);
        public Desc descriptor() { return desc; }

        final int val;
        @ValueConstructor
        public BotInt(int val) {
            this.val = val;
        }

    }

    @Ident(Top.class)
    public static class BotString extends Top {
        static Desc<BotString> desc = Desc.get(BotString.class);
        public Desc descriptor() { return desc; }

        final String v;
        @ValueConstructor
        public BotString(String v) {
            this.v = v;
        }
    }

    public static void main(String[] args) {
        IRStorage store = new IRStorage();

        store.get(BotString.desc, Arrays.asList("coucou"));
        store.get(BotInt.desc, Arrays.asList(1));
        store.get(BotString.desc, Arrays.asList("coucou"));

//        System.out.println(store.get(Top.class, 0));
//        System.out.println(store.get(Top.class, 1));


    }
}
