package io.github.paypal;

import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import io.github.Store;
import io.github.utils.ClickableMessage;
import io.github.utils.PendingTransactions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DepositHandler extends PaypalClient {
    private final Store store;

    public DepositHandler(Store store) {
        super(store);
        this.store = store;
    }

    @SuppressWarnings("unchecked")
    public void createDepositOrder(double cost, String item, Player player) {
        store.getServer().getScheduler().runTaskAsynchronously(store, () -> {
            double total;

            if (store.getConfig().getBoolean("ITEMS." + item + ".PASS_FEE")) {
                total = (1000.0 / 956.0) * (cost + 0.30);
            } else {
                total = cost;
            }

            DecimalFormat df = new DecimalFormat("#.##");

            OrdersCreateRequest request = new OrdersCreateRequest();
            request.header("prefer", "return=representation");

            OrderRequest orderRequest = new OrderRequest();
            orderRequest.checkoutPaymentIntent("CAPTURE");
            PaymentMethod method = new PaymentMethod().payeePreferred("IMMEDIATE_PAYMENT_REQUIRED");
            ApplicationContext context = new ApplicationContext()
                    .brandName(store.getConfig().getString("BRAND_NAME"))
                    .landingPage("LOGIN")
                    .userAction("PAY_NOW")
                    .shippingPreference("NO_SHIPPING")
                    .paymentMethod(method);
            orderRequest.applicationContext(context);
            List<PurchaseUnitRequest> unitRequests = new ArrayList<>();
            PurchaseUnitRequest unitRequest = new PurchaseUnitRequest()
                    .description("$" + df.format(cost) + " deposit for " + player.getUniqueId().toString())
                    .amountWithBreakdown(new AmountWithBreakdown().currencyCode("USD").value(df.format(total)));
            unitRequests.add(unitRequest);
            orderRequest.purchaseUnits(unitRequests);
            request.requestBody(orderRequest);

            HttpResponse<Order> response;

            try {
                response = client.execute(request);
            } catch (IOException e) {
                store.getLogger().severe("Failed creating PayPal order: " + e.getMessage());
                return;
            }

            Logger logger = store.getLogger();
            logger.info("");
            logger.info("New deposit!");
            logger.info("UUID: " + player.getUniqueId());
            logger.info("Item: " + item);
            logger.info("Cost: $" + df.format(cost));
            logger.info("Total: $" + df.format(total));
            logger.info("ID: " + response.result().id());
            logger.info("");

            JSONObject pendingTransaction = new JSONObject();
            pendingTransaction.put("playerUUID", player.getUniqueId().toString());
            pendingTransaction.put("item", item);
            pendingTransaction.put("cost", cost);
            pendingTransaction.put("expiry", System.currentTimeMillis() + store.getConfig().getLong("EXPIRE_AFTER"));

            PendingTransactions pendingTransactions = store.getPendingTransactions();
            pendingTransactions.addPendingTransaction(response.result().id(), pendingTransaction);

            String paymentUrl = response.result().links().stream()
                    .filter(linkDescription -> "approve".equalsIgnoreCase(linkDescription.rel()))
                    .findFirst()
                    .map(linkDescription -> linkDescription.href())
                    .orElse(null);

            if (paymentUrl == null) {
                store.getLogger().severe("PayPal response did not contain an approval URL.");
                return;
            }

            String finalPaymentUrl = paymentUrl;
            store.getServer().getScheduler().runTask(store, () -> {
                ClickableMessage linkButton = new ClickableMessage(ChatColor.translateAlternateColorCodes('&', store.getConfig().getString("LINK_BUTTON")))
                        .hover(ChatColor.translateAlternateColorCodes('&', store.getConfig().getString("LINK_BUTTON_HOVER")))
                        .link(finalPaymentUrl);

                linkButton.add(ChatColor.translateAlternateColorCodes('&', store.getConfig().getString("CANCEL_BUTTON")))
                        .hover(ChatColor.translateAlternateColorCodes('&', store.getConfig().getString("CANCEL_BUTTON_HOVER")))
                        .command(store.getConfig().getString("CANCEL_BUTTON_COMMAND"));

                linkButton.add(ChatColor.translateAlternateColorCodes('&', store.getConfig().getString("TIMER_BUTTON")))
                        .hover(ChatColor.translateAlternateColorCodes('&', store.getConfig().getString("TIMER_BUTTON_HOVER")))
                        .command(store.getConfig().getString("TIMER_BUTTON_COMMAND"));

                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        store.getConfig().getString("PAYMENT_MESSAGE")
                                .replace("{link}", finalPaymentUrl)
                                .replace("{cost-before-fees}", df.format(cost))
                                .replace("{cost-after-fees}", df.format(total))));

                linkButton.sendToPlayer(player);
            });
        });
    }
}
