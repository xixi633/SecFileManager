import { createRouter, createWebHistory } from "vue-router";
import Login from "../views/Login.vue";
import Layout from "../views/Layout.vue";
import FileList from "../views/FileList.vue";
import UserSettings from "../views/UserSettings.vue";
import SecurityInfo from "../views/SecurityInfo.vue";
import AdminPanel from "../views/AdminPanel.vue";
import RecycleBin from "../views/RecycleBin.vue";
import Chat from "../views/Chat.vue";

const routes = [
  { path: "/", redirect: "/files" },
  { path: "/login", name: "Login", component: Login },
  { path: "/admin", name: "Admin", component: AdminPanel },
  {
    path: "/",          
    component: Layout,
    children: [
      { path: "files", name: "Files", component: FileList },
      { path: "recycle", name: "Recycle", component: RecycleBin },
      { path: "settings", name: "Settings", component: UserSettings },
      { path: "security", name: "Security", component: SecurityInfo },
      { path: "chat", name: "Chat", component: Chat }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role");
  
  // 未登录跳转登录页
  if (to.path !== "/login" && !token) {
    return "/login";
  }
  
  // 已登录访问登录页，根据角色跳转
  if (to.path === "/login" && token) {
    return role === "admin" ? "/admin" : "/files";
  }
  
  // 管理员页面权限检查
  if (to.path === "/admin" && role !== "admin") {
    return "/files";
  }
  
  return true;
});

export default router;
