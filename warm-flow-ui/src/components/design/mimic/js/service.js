import {BaseNodeModel} from "@/components/design/mimic/js/baseNodeModel";
import {BaseNodeView} from "@/components/design/mimic/js/baseNodeView";

class ServiceModel extends BaseNodeModel {}

class ServiceView extends BaseNodeView {}

export default {
  type: "service",
  model: ServiceModel,
  view: ServiceView,
};
