using System;
using System.IO;
using System.Security.Cryptography;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;

// Editor-only, standalone project. Never opens the Android project or physical photos.
public static class CardPilot {
 [Serializable] public class TextureRow { public string path, sha256, group, split; }
 [Serializable] public class Config { public int seed=20261028, count=120, width=640, height=896; public string output; public TextureRow[] textures; }
 [Serializable] public class XY { public float x,y; public XY(float a,float b){x=a;y=b;} }
 [Serializable] public class Label {
  public int schema=2,index,seed,width,height; public string image,imageSha256,textureSha256,group,split;
  public string cornerOrder="texture-TL,TR,BR,BL", coordinates="normalized-top-left", generator="unity-card-pilot-v2";
  public string project="Esquinitas";
  public bool[] cornerVisible; public bool thumbOccluder; public Vector3 thumbLocalPosition,thumbScale;
  public bool present,sleeve,foilApproximation; public XY[] corners; public Vector3 rotation,position,light;
  public float lightIntensity,cameraDistance; public string unityVersion;
 }
 static float R(float a,float b)=>UnityEngine.Random.Range(a,b);
 static string Hash(byte[] b){using(var h=SHA256.Create())return BitConverter.ToString(h.ComputeHash(b)).Replace("-","").ToLowerInvariant();}
 static Mesh Quad(float w,float h) {
  var m=new Mesh(); m.vertices=new[]{new Vector3(-w,h,0),new Vector3(w,h,0),new Vector3(w,-h,0),new Vector3(-w,-h,0)};
  m.uv=new[]{new Vector2(0,1),new Vector2(1,1),new Vector2(1,0),new Vector2(0,0)};
  m.triangles=new[]{0,1,2,0,2,3};m.RecalculateNormals(); return m;
 }
 static GameObject Surface(string name,Mesh mesh,Material mat) {
  var o=new GameObject(name);o.AddComponent<MeshFilter>().sharedMesh=mesh;o.AddComponent<MeshRenderer>().sharedMaterial=mat;return o;
 }
 public static void Generate() {
  var path=Environment.GetEnvironmentVariable("CARD_PILOT_CONFIG");
  if(string.IsNullOrEmpty(path))throw new Exception("Set CARD_PILOT_CONFIG to an absolute config.json path.");
  var cfg=JsonUtility.FromJson<Config>(File.ReadAllText(path));
  if(cfg.count<1||cfg.count>10000||cfg.width<64||cfg.height<64||cfg.width>2048||cfg.height>2048||cfg.textures==null||cfg.textures.Length<2)throw new Exception("Invalid bounded config");
  if(!Path.IsPathRooted(cfg.output)||Directory.Exists(cfg.output))throw new Exception("Output must be NEW absolute directory");
  foreach(var t in cfg.textures) {
   if(!Path.IsPathRooted(t.path)||Hash(File.ReadAllBytes(t.path))!=t.sha256)throw new Exception("Texture SHA mismatch");
   if(t.split!="train"&&t.split!="validation")throw new Exception("Invalid split");
  }
  // Prevent texture/group leakage before any render.
  for(int a=0;a<cfg.textures.Length;a++) for(int b=0;b<a;b++)
   if((cfg.textures[a].group==cfg.textures[b].group||cfg.textures[a].sha256==cfg.textures[b].sha256)&&cfg.textures[a].split!=cfg.textures[b].split)throw new Exception("Split leakage");
  if(!Application.isBatchMode && !EditorSceneManager.SaveCurrentModifiedScenesIfUserWantsTo())return;
  EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Single);
  Directory.CreateDirectory(cfg.output); File.Copy(path,Path.Combine(cfg.output,"config.json"));
  var shader=Shader.Find("Pilot/CardSurface");if(shader==null)throw new Exception("Missing shader");
  var camera=new GameObject("Camera").AddComponent<Camera>();camera.fieldOfView=45;camera.nearClipPlane=.01f;camera.farClipPlane=10;
  camera.clearFlags=CameraClearFlags.SolidColor;camera.aspect=(float)cfg.width/cfg.height;
  var rt=new RenderTexture(cfg.width,cfg.height,24);rt.Create();camera.targetTexture=rt;
  var read=new Texture2D(cfg.width,cfg.height,TextureFormat.RGB24,false);
  var mesh=Quad(.0315f,.044f);var sleeveMesh=Quad(.034f,.047f);var bgMesh=Quad(1,1);
  var cardMat=new Material(shader);var sleeveMat=new Material(shader);sleeveMat.SetFloat("_Sleeve",1);sleeveMat.SetFloat("_Gloss",2);
  var bgMat=new Material(shader);var card=Surface("Card",mesh,cardMat);var sleeve=Surface("Sleeve",sleeveMesh,sleeveMat);sleeve.transform.SetParent(card.transform,false);sleeve.transform.localPosition=new Vector3(0,0,-.0002f);
  var bg=Surface("Background",bgMesh,bgMat);bg.transform.position=new Vector3(0,0,.05f);
  // Ellipsoid proxy, NOT a realistic hand. Collider and rendered surface share geometry.
  var thumb=GameObject.CreatePrimitive(PrimitiveType.Sphere);thumb.name="ThumbProxy";
  thumb.transform.SetParent(card.transform,false);var thumbMat=new Material(shader);
  thumb.GetComponent<Renderer>().sharedMaterial=thumbMat;
  // SphereCollider uses a sphere under nonuniform scale: use the actual rendered mesh.
  UnityEngine.Object.DestroyImmediate(thumb.GetComponent<Collider>());
  var thumbCollider=thumb.AddComponent<MeshCollider>();thumbCollider.sharedMesh=thumb.GetComponent<MeshFilter>().sharedMesh;
  try {
   for(int i=0;i<cfg.count;i++) {
    UnityEngine.Random.InitState(cfg.seed+i);var t=cfg.textures[i%cfg.textures.Length];
    var tex=new Texture2D(2,2);if(!tex.LoadImage(File.ReadAllBytes(t.path)))throw new Exception("Cannot decode texture");
    var bgTex=new Texture2D(32,32);var pixels=new Color[1024];var c1=UnityEngine.Random.ColorHSV(0,1,0,.5f,.15f,.9f);var c2=UnityEngine.Random.ColorHSV(0,1,0,.5f,.15f,.9f);
    for(int p=0;p<pixels.Length;p++)pixels[p]=((p/32/4+p%32/4)%2==0?c1:c2);bgTex.SetPixels(pixels);bgTex.Apply();bgMat.mainTexture=bgTex;
    bool present=i%10!=0,foil=R(0,1)<.35f,plastic=R(0,1)<.5f;
    card.SetActive(present);sleeve.SetActive(plastic);cardMat.mainTexture=tex;cardMat.SetFloat("_Gloss",foil?1.4f:.12f);cardMat.SetFloat("_Foil",foil?1:0);
    camera.transform.position=new Vector3(0,0,-R(.16f,.25f));
    var light=new Vector3(R(-.2f,.2f),R(-.2f,.2f),R(-.3f,-.1f));float intensity=R(.5f,1.5f);
    Shader.SetGlobalVector("_PilotLight",light);Shader.SetGlobalFloat("_PilotIntensity",intensity);
    XY[] corners=new XY[0];bool inside=false;
    for(int attempt=0;attempt<100;attempt++) {
     card.transform.rotation=Quaternion.Euler(R(-42,42),R(-42,42),R(-30,30));card.transform.position=new Vector3(R(-.018f,.018f),R(-.025f,.025f),0);
     corners=new XY[4];inside=true;
     for(int k=0;k<4;k++){var v=camera.WorldToViewportPoint(card.transform.TransformPoint(mesh.vertices[k]));corners[k]=new XY(v.x,1-v.y);inside &= v.z>0&&v.x>.025&&v.x<.975&&v.y>.025&&v.y<.975;}
     if(inside)break;
    }
    if(!inside)throw new Exception("Failed to sample complete card");
    bool occluded=present&&R(0,1)<.4f;thumb.SetActive(occluded);
    int covered=UnityEngine.Random.Range(0,4);var corner=mesh.vertices[covered];
    thumb.transform.localPosition=new Vector3(corner.x*R(.85f,1.05f),corner.y*R(.85f,1.05f),-.007f);
    thumb.transform.localScale=new Vector3(R(.014f,.025f),R(.022f,.037f),.012f);
    thumbMat.SetColor("_Tint",Color.Lerp(new Color(.22f,.10f,.06f),new Color(.95f,.71f,.55f),R(0,1)));
    Physics.SyncTransforms();var visible=present?new bool[4]:new bool[0];
    for(int k=0;k<visible.Length;k++) {
     var target=card.transform.TransformPoint(mesh.vertices[k]);var delta=target-camera.transform.position;
     RaycastHit hit;visible[k]=!occluded||!thumbCollider.Raycast(new Ray(camera.transform.position,delta.normalized),out hit,delta.magnitude);
    }
    camera.Render();RenderTexture.active=rt;read.ReadPixels(new Rect(0,0,cfg.width,cfg.height),0,0);read.Apply();var png=read.EncodeToPNG();
    string name=i.ToString("D6")+".png";File.WriteAllBytes(Path.Combine(cfg.output,name),png);
    var label=new Label{index=i,seed=cfg.seed+i,width=cfg.width,height=cfg.height,image=name,imageSha256=Hash(png),textureSha256=t.sha256,group=t.group,split=t.split,present=present,corners=present?corners:new XY[0],sleeve=plastic,foilApproximation=foil,rotation=card.transform.eulerAngles,position=card.transform.position,light=light,lightIntensity=intensity,cameraDistance=-camera.transform.position.z,unityVersion=Application.unityVersion};
    label.cornerVisible=visible;label.thumbOccluder=occluded;label.thumbLocalPosition=thumb.transform.localPosition;label.thumbScale=thumb.transform.localScale;
    File.WriteAllText(Path.Combine(cfg.output,i.ToString("D6")+".json"),JsonUtility.ToJson(label,true));
    UnityEngine.Object.DestroyImmediate(tex);UnityEngine.Object.DestroyImmediate(bgTex);
   }
   File.WriteAllText(Path.Combine(cfg.output,"COMPLETE"),cfg.count.ToString());
  } finally { RenderTexture.active=null;camera.targetTexture=null;rt.Release();UnityEngine.Object.DestroyImmediate(rt);UnityEngine.Object.DestroyImmediate(read); }
 }
}
