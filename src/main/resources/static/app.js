const catalog = {
  "school-kit": {name:"Books & stationery", price:25000},
  "food-pack": {name:"School fees support", price:50000},
  "health-kit": {name:"Digital learning kit", price:75000}
};
const cart = {};
const money = n => "UGX " + new Intl.NumberFormat("en-UG").format(n);
const $ = id => document.getElementById(id);
const cartItems = $("cartItems");
const cartTotal = $("cartTotal");
const cartCount = $("cartCount");
const checkoutBtn = $("checkoutBtn");
const dialog = $("checkoutDialog");

function total(){return Object.entries(cart).reduce((sum,[id,qty])=>sum+catalog[id].price*qty,0)}
function renderCart(){
  const entries=Object.entries(cart).filter(([,qty])=>qty>0);
  cartCount.textContent=entries.reduce((n,[,q])=>n+q,0);
  cartTotal.textContent=money(total());
  $("dialogTotal").textContent=money(total());
  checkoutBtn.disabled=entries.length===0;
  if(!entries.length){cartItems.innerHTML='<div class="empty-cart"><span>♡</span><p>Your gift list is waiting.</p><small>Add a gift to get started.</small></div>';return;}
  cartItems.innerHTML=entries.map(([id,qty])=>`<div class="cart-row"><div><strong>${catalog[id].name}</strong><small>${money(catalog[id].price)} each · ${money(catalog[id].price*qty)}</small></div><div class="qty-controls"><button type="button" data-action="minus" data-id="${id}" aria-label="Remove one ${catalog[id].name}">−</button><span>${qty}</span><button type="button" data-action="plus" data-id="${id}" aria-label="Add one ${catalog[id].name}">+</button></div></div>`).join("");
}
function toast(message){const el=$("toast");el.textContent=message;el.classList.add("show");setTimeout(()=>el.classList.remove("show"),2400)}
document.querySelectorAll(".add-btn").forEach(btn=>btn.addEventListener("click",()=>{const id=btn.dataset.id;cart[id]=(cart[id]||0)+1;renderCart();toast(catalog[id].name+" added to your gift list");}));
cartItems.addEventListener("click",e=>{const btn=e.target.closest("button[data-action]");if(!btn)return;const {id,action}=btn.dataset;cart[id]=Math.max(0,(cart[id]||0)+(action==="plus"?1:-1));if(!cart[id])delete cart[id];renderCart();});
checkoutBtn.addEventListener("click",()=>{ $("formError").textContent="";dialog.showModal();});
$("closeDialog").addEventListener("click",()=>dialog.close());
$("donorForm").addEventListener("submit",async e=>{
  e.preventDefault();
  const form=e.currentTarget, pay=$("payBtn"), error=$("formError");
  error.textContent="";
  if(!Object.keys(cart).length){error.textContent="Please add at least one gift.";return;}
  const payload={name:$("donorName").value.trim(),email:$("donorEmail").value.trim(),phone:$("donorPhone").value.trim(),items:Object.entries(cart).map(([id,quantity])=>({id,quantity}))};
  pay.disabled=true;pay.textContent="Connecting securely…";
  try{
    const response=await fetch("/api/checkout",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
    const data=await response.json().catch(()=>({}));
    if(!response.ok)throw new Error(data.message||"Unable to start checkout. Please try again.");
    if(!data.checkoutUrl)throw new Error("The payment provider did not return a checkout link.");
    window.location.assign(data.checkoutUrl);
  }catch(err){error.textContent=err.message||"Something went wrong.";pay.disabled=false;pay.innerHTML='Proceed to secure payment <span>→</span>';}
});
$("year").textContent=new Date().getFullYear();
renderCart();
