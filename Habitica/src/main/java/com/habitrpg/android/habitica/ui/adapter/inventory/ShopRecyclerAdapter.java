package com.habitrpg.android.habitica.ui.adapter.inventory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.common.executors.CallerThreadExecutor;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.habitrpg.android.habitica.R;
import com.habitrpg.android.habitica.events.commands.OpenGemPurchaseFragmentCommand;
import com.habitrpg.android.habitica.helpers.RxErrorHandler;
import com.habitrpg.android.habitica.models.inventory.Item;
import com.habitrpg.android.habitica.models.shops.Shop;
import com.habitrpg.android.habitica.models.shops.ShopCategory;
import com.habitrpg.android.habitica.models.shops.ShopItem;
import com.habitrpg.android.habitica.models.user.User;
import com.habitrpg.android.habitica.ui.helpers.DataBindingUtils;
import com.habitrpg.android.habitica.ui.viewHolders.SectionViewHolder;
import com.habitrpg.android.habitica.ui.viewHolders.ShopItemViewHolder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.RealmResults;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

public class ShopRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Object> items;
    private String shopIdentifier;
    private Map<String, Item> ownedItems = new HashMap<>();
    private String shopSpriteSuffix;
    private User user;
    private List<String> pinnedItemKeys;

    public void setShop(Shop shop, String shopSpriteSuffix) {
        this.shopSpriteSuffix = shopSpriteSuffix;
        shopIdentifier = shop.identifier;
        items = new ArrayList<>();
        items.add(shop);
        for (ShopCategory category : shop.categories) {
            if (category.items != null && category.items.size() > 0) {
                items.add(category);
                for (ShopItem item : category.items) {
                    item.categoryIdentifier = category.getIdentifier();
                    items.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.shop_header, parent, false);

            return new ShopHeaderViewHolder(view);
        } else if (viewType == 1) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.shop_section_header, parent, false);

            return new SectionViewHolder(view);
        } else if (viewType == 2) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(getEmptyViewResource(), parent, false);
            return new EmptyStateViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_shopitem, parent, false);
            ShopItemViewHolder viewHolder = new ShopItemViewHolder(view);
            viewHolder.shopIdentifier = shopIdentifier;
            return viewHolder;
        }
    }

    private int getEmptyViewResource() {
        if (Shop.SEASONAL_SHOP.equals(this.shopIdentifier)) {
            return R.layout.empty_view_seasonal_shop;
        } else if (Shop.TIME_TRAVELERS_SHOP.equals(this.shopIdentifier)) {
            return R.layout.empty_view_timetravelers;
        }
        return R.layout.simple_textview;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (position > (this.items.size()-1)) {
            return;
        }
        Object obj = this.items.get(position);
        if (obj.getClass().equals(Shop.class)) {
            ((ShopHeaderViewHolder) holder).bind((Shop) obj, shopSpriteSuffix);
        } else if (obj.getClass().equals(ShopCategory.class)) {
            ((SectionViewHolder) holder).bind(((ShopCategory) obj).getText());
        } else if (obj.getClass().equals(ShopItem.class)) {
            ShopItem item = (ShopItem) items.get(position);
            ((ShopItemViewHolder) holder).bind(item, item.canBuy(user));
            if (ownedItems.containsKey(item.getKey())) {
                ((ShopItemViewHolder) holder).setItemCount(ownedItems.get(item.getKey()).getOwned());
            }
            if (pinnedItemKeys != null) {
                ((ShopItemViewHolder) holder).setIsPinned(pinnedItemKeys.contains(item.getKey()));
            } else {
                ((ShopItemViewHolder) holder).setIsPinned(false);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position > (this.items.size()-1)) {
            return 2;
        } else if (this.items.get(position).getClass().equals(Shop.class)) {
            return 0;
        } else if (this.items.get(position).getClass().equals(ShopCategory.class)) {
            return 1;
        } else {
            return 3;
        }
    }

    @Override
    public int getItemCount() {
        int size = items != null ? items.size() : 0;
        if (size == 1) {
            return 2;
        }
        return size;
    }

    public void setOwnedItems(Map<String, Item> ownedItems) {
        this.ownedItems = ownedItems;
        this.notifyDataSetChanged();
    }

    public void setUser(User user) {
        this.user = user;
        this.notifyDataSetChanged();
    }

    public void setPinnedItemKeys(List<String> pinnedItemKeys) {
        this.pinnedItemKeys = pinnedItemKeys;
        this.notifyDataSetChanged();
    }

    static class ShopHeaderViewHolder extends RecyclerView.ViewHolder {

        private final Context context;
        @BindView(R.id.sceneView)
        public SimpleDraweeView sceneView;
        @BindView(R.id.backgroundView)
        public ImageView backgroundView;

        @BindView(R.id.name_plate)
        public TextView namePlate;

        @BindView(R.id.descriptionView)
        public TextView descriptionView;


        ShopHeaderViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            context = itemView.getContext();
            descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        public void bind(Shop shop, String shopSpriteSuffix) {
            DataBindingUtils.loadImage(sceneView, shop.identifier+"_scene"+shopSpriteSuffix);

            backgroundView.setScaleType(ImageView.ScaleType.FIT_START);

            ImageRequest imageRequest = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse("https://habitica-assets.s3.amazonaws.com/mobileApp/images/" + shop.identifier+"_background"+shopSpriteSuffix+".png"))
                    .build();

            ImagePipeline imagePipeline = Fresco.getImagePipeline();
            final DataSource<CloseableReference<CloseableImage>>
                    dataSource = imagePipeline.fetchDecodedImage(imageRequest, this);

            dataSource.subscribe(new BaseBitmapDataSubscriber() {

                @Override
                public void onNewResultImpl(@Nullable Bitmap bitmap) {
                    if (dataSource.isFinished() && bitmap != null){
                        float aspectRatio = bitmap.getWidth() /
                                (float) bitmap.getHeight();
                        int height = (int) context.getResources().getDimension(R.dimen.shop_height);
                        int width = Math.round(height * aspectRatio);
                        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), Bitmap.createScaledBitmap(bitmap, width, height, false));
                        drawable.setTileModeX(Shader.TileMode.REPEAT);
                        Observable.just(drawable)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(bitmapDrawable -> backgroundView.setBackground(bitmapDrawable), RxErrorHandler.handleEmptyError());
                        dataSource.close();
                    }
                }

                @Override
                public void onFailureImpl(DataSource dataSource) {
                    if (dataSource != null) {
                        dataSource.close();
                    }
                }
            }, CallerThreadExecutor.getInstance());

            descriptionView.setText(Html.fromHtml(shop.getNotes()));
            namePlate.setText(shop.getNpcNameResource());
        }

    }

    public static class EmptyStateViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.subscribeButton)
        @Nullable
        Button subscribeButton;

        public EmptyStateViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);

            if (subscribeButton != null) {
                subscribeButton.setOnClickListener(view1 -> EventBus.getDefault().post(new OpenGemPurchaseFragmentCommand()));
            }
        }
    }
}
